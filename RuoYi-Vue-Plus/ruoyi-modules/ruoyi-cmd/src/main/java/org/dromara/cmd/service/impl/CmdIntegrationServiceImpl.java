package org.dromara.cmd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.IntEndpoint;
import org.dromara.cmd.domain.IntRun;
import org.dromara.cmd.domain.bo.IntConnBo;
import org.dromara.cmd.domain.bo.IntRunBo;
import org.dromara.cmd.domain.vo.IntEndpointVo;
import org.dromara.cmd.domain.vo.IntRunVo;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.IntEndpointMapper;
import org.dromara.cmd.mapper.IntRunMapper;
import org.dromara.cmd.service.ICmdIntegrationService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 集成监控 / 集成配置 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>端点可配置：目标系统 / 协议 / 地址 / 认证 / 报文格式 / 重试策略，真实系统到位后仅需改配置</li>
 *   <li>模拟下游：POC 阶段内置 mock 接收通道（URL 前缀 mock:// 走本地模拟；
 *       配置 {@code /cmd/integration/mock/deliver} 走真实 HTTP，可演示成功 / fail 故障两种通道）</li>
 *   <li>发布 = 把启用的客户主数据组装成报文推送到端点，落 int_run 运行记录</li>
 *   <li>重试 = 按原端点 / 条数重新推送，并累加尝试次数，不伪造成功结果</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdIntegrationServiceImpl implements ICmdIntegrationService {

    private final IntRunMapper runMapper;
    private final IntEndpointMapper endpointMapper;
    private final CmdCustomerMapper customerMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 本应用内置接收台基址（local://、mock:// 地址回环到此处做真实 HTTP 调用）。
     * 可通过配置 {@code cmd.integration.receiver-base-url} 覆盖（默认本机 8080）。
     */
    @Value("${cmd.integration.receiver-base-url:http://localhost:8080}")
    private String receiverBaseUrl;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<IntRunVo> selectPage(IntRunBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<IntRun> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(bo.getRunCode())) {
            lqw.eq(IntRun::getRunCode, bo.getRunCode());
        }
        if (StringUtils.isNotBlank(bo.getDirection())) {
            lqw.eq(IntRun::getDirection, bo.getDirection());
        }
        if (StringUtils.isNotBlank(bo.getTargetSystem())) {
            lqw.eq(IntRun::getTargetSystem, bo.getTargetSystem());
        }
        if (StringUtils.isNotBlank(bo.getRunStatus())) {
            lqw.eq(IntRun::getRunStatus, bo.getRunStatus());
        }
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            lqw.and(w -> w.like(IntRun::getRunCode, bo.getKeyword())
                .or().like(IntRun::getTargetSystem, bo.getKeyword()));
        }
        lqw.orderByDesc(IntRun::getStartTime);
        Page<IntRunVo> page = runMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String retry(String runCode) {
        IntRun run = runMapper.selectOne(Wrappers.<IntRun>lambdaQuery().eq(IntRun::getRunCode, runCode));
        if (run == null) {
            throw new ServiceException("集成运行记录不存在：{}", runCode);
        }
        IntEndpoint endpoint = endpointMapper.selectOne(
            Wrappers.<IntEndpoint>lambdaQuery().eq(IntEndpoint::getEndpointCode, run.getEndpointCode()));
        int count = (run.getTotalCount() == null || run.getTotalCount() < 1) ? 5 : run.getTotalCount();
        int attempt = (run.getAttemptCount() == null ? 0 : run.getAttemptCount()) + 1;
        try {
            doDeliver(endpoint, count, runCode);
            IntRun update = new IntRun();
            update.setId(run.getId());
            update.setRunStatus("SUCCESS");
            update.setSuccessCount(count);
            update.setFailedCount(0);
            update.setAttemptCount(attempt);
            update.setErrorMessage(null);
            update.setEndTime(LocalDateTime.now());
            runMapper.updateById(update);
            return "重试成功：" + runCode + "（" + count + " 条已送达，第 " + attempt + " 次）";
        } catch (Exception e) {
            IntRun update = new IntRun();
            update.setId(run.getId());
            update.setRunStatus("FAILED");
            update.setFailedCount(count);
            update.setAttemptCount(attempt);
            update.setErrorMessage(truncate(e.getMessage()));
            update.setEndTime(LocalDateTime.now());
            runMapper.updateById(update);
            return "重试失败：" + runCode + "（第 " + attempt + " 次：" + e.getMessage() + "）";
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveConn(IntConnBo bo) {
        if (bo.getId() == null && StringUtils.isBlank(bo.getSystem())) {
            throw new ServiceException("目标系统不能为空");
        }
        String endpointCode = "EP-" + StringUtils.blankToDefault(bo.getSystem(), "").toUpperCase().replaceAll("[^A-Z0-9]", "");
        IntEndpoint exist = bo.getId() != null
            ? endpointMapper.selectById(bo.getId())
            : endpointMapper.selectOne(
                Wrappers.<IntEndpoint>lambdaQuery().eq(IntEndpoint::getEndpointCode, endpointCode));
        String remark = "同步周期：" + StringUtils.blankToDefault(bo.getPeriod(), "未提供");
        if (exist != null) {
            exist.setEndpointCode(endpointCode);
            exist.setEndpointName(StringUtils.blankToDefault(bo.getName(), bo.getSystem()));
            exist.setProtocol(StringUtils.blankToDefault(bo.getProtocol(), exist.getProtocol()));
            exist.setEndpointUrl(bo.getUrl());
            exist.setTargetSystem(bo.getSystem());
            exist.setDirection(StringUtils.blankToDefault(bo.getDirection(), exist.getDirection()));
            exist.setAuthType(StringUtils.blankToDefault(bo.getAuthType(), exist.getAuthType()));
            exist.setBizType(bo.getBizType());
            exist.setMessageFormat(StringUtils.blankToDefault(bo.getMessageFormat(), exist.getMessageFormat()));
            exist.setMaxRetry(bo.getMaxRetry() == null ? exist.getMaxRetry() : bo.getMaxRetry());
            exist.setTimeoutMs(bo.getTimeoutMs() == null ? exist.getTimeoutMs() : bo.getTimeoutMs());
            exist.setStatus(StringUtils.blankToDefault(bo.getStatus(), exist.getStatus()));
            exist.setRemark(remark);
            endpointMapper.updateById(exist);
            return endpointCode + "（" + exist.getEndpointName() + " 已更新）";
        }
        IntEndpoint entity = new IntEndpoint();
        entity.setEndpointCode(endpointCode);
        entity.setEndpointName(StringUtils.blankToDefault(bo.getName(), bo.getSystem()));
        entity.setDirection(StringUtils.blankToDefault(bo.getDirection(), "OUTBOUND"));
        entity.setProtocol(StringUtils.blankToDefault(bo.getProtocol(), "HTTP"));
        entity.setTargetSystem(bo.getSystem());
        entity.setEndpointUrl(bo.getUrl());
        entity.setAuthType(StringUtils.blankToDefault(bo.getAuthType(), "NONE"));
        entity.setBizType(bo.getBizType());
        entity.setMessageFormat(StringUtils.blankToDefault(bo.getMessageFormat(), "JSON"));
        entity.setMaxRetry(bo.getMaxRetry() == null ? 3 : bo.getMaxRetry());
        entity.setTimeoutMs(bo.getTimeoutMs() == null ? 30000 : bo.getTimeoutMs());
        entity.setStatus(StringUtils.blankToDefault(bo.getStatus(), "0"));
        entity.setRemark(remark);
        endpointMapper.insert(entity);
        return endpointCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IntEndpointVo> listEndpoints() {
        return endpointMapper.selectVoList(
            Wrappers.<IntEndpoint>lambdaQuery().orderByDesc(IntEndpoint::getUpdateTime));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteEndpoint(Long id) {
        IntEndpoint endpoint = endpointMapper.selectById(id);
        if (endpoint == null) {
            throw new ServiceException("端点不存在：{}", id);
        }
        endpointMapper.deleteById(id);
        return "端点已删除：" + endpoint.getEndpointName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String testConn(Long endpointId) {
        IntEndpoint endpoint = endpointMapper.selectById(endpointId);
        if (endpoint == null) {
            throw new ServiceException("端点不存在：{}", endpointId);
        }
        String url = StringUtils.blankToDefault(endpoint.getEndpointUrl(), "");
        String system = StringUtils.blankToDefault(endpoint.getTargetSystem(), endpoint.getEndpointName());
        int timeout = timeoutOf(endpoint);
        // local:// 或 mock:// → 回环到本应用内置接收台发送真实探活报文
        if (url.startsWith("local://") || url.startsWith("mock://")) {
            String receiver = localReceiverUrl(url);
            Map<String, Object> probe = new HashMap<>();
            probe.put("probe", true);
            probe.put("endpointCode", endpoint.getEndpointCode());
            probe.put("time", LocalDateTime.now().toString());
            long start = System.currentTimeMillis();
            try {
                String resp = httpPost(receiver, objectMapper.writeValueAsString(probe), timeout);
                long cost = System.currentTimeMillis() - start;
                return "连通性测试通过（HTTP 200，耗时 " + cost + "ms）：" + system
                    + " → " + receiver + "；应答 " + truncate(resp);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                return "连通性测试失败（耗时 " + cost + "ms）：" + system
                    + " → " + receiver + "；" + truncate(e.getMessage());
            }
        }
        // http(s):// → 真实网络探活（GET），返回实际状态码与耗时
        if (url.startsWith("http://") || url.startsWith("https://")) {
            long start = System.currentTimeMillis();
            try {
                int code = httpGet(url, timeout);
                long cost = System.currentTimeMillis() - start;
                return "连通性测试通过（HTTP " + code + "，耗时 " + cost + "ms）：" + system + " → " + url;
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                return "连通性测试失败（耗时 " + cost + "ms）：" + system + " → " + url
                    + "；" + truncate(e.getMessage());
            }
        }
        return "端点地址无法识别，无法执行连通性测试：" + url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String publishToSystem(Long endpointId, Integer count) {
        IntEndpoint endpoint = endpointMapper.selectById(endpointId);
        if (endpoint == null) {
            throw new ServiceException("端点不存在：{}", endpointId);
        }
        if ("1".equals(endpoint.getStatus())) {
            throw new ServiceException("端点已停用，请先在集成配置中启用");
        }
        int n = (count == null || count < 1) ? 5 : Math.min(count, 50);
        String runCode = genRunCode();
        try {
            String resp = doDeliver(endpoint, n, runCode);
            writeRun(endpoint, runCode, "SUCCESS", n, resp, 1);
            return "发布成功：" + runCode + "（" + n + " 条客户主数据已送达 " + endpoint.getTargetSystem() + "）";
        } catch (Exception e) {
            writeRun(endpoint, runCode, "FAILED", n, e.getMessage(), 1);
            return "发布失败：" + runCode + "（" + e.getMessage() + "），可在运行监控中 Retry";
        }
    }

    /**
     * 执行一次推送：组装客户主数据报文 → 发送到端点地址
     *
     * @param endpoint 端点配置
     * @param count    推送条数
     * @param runCode  运行编号
     * @return 目标系统应答内容
     */
    private String doDeliver(IntEndpoint endpoint, int count, String runCode) throws Exception {
        Map<String, Object> payload = buildPayload(count);
        payload.put("runCode", runCode);
        String json = objectMapper.writeValueAsString(payload);
        String url = StringUtils.blankToDefault(endpoint.getEndpointUrl(), "");
        // local:// 或 mock:// → 回环到本应用内置接收台（真实 HTTP POST）
        if (url.startsWith("local://") || url.startsWith("mock://")) {
            return httpPost(localReceiverUrl(url), json, timeoutOf(endpoint));
        }
        // http(s):// → 真实网络推送
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return httpPost(url, json, timeoutOf(endpoint));
        }
        throw new IOException("端点地址无法识别，无法发布：" + url);
    }

    /**
     * 将 local:// / mock:// 地址解析为本应用内置接收台的真实 HTTP 地址（回环）。
     * 含 "fail" 的地址指向故障台（HTTP 500），用于演示失败 / Retry。
     */
    private String localReceiverUrl(String url) {
        String path = url.contains("fail") ? "cmd/integration/mock/deliver/fail" : "cmd/integration/mock/deliver";
        return receiverBaseUrl + "/" + path;
    }

    /**
     * 轻量 HTTP GET（JDK 原生），用于连通性探活；返回实际状态码。
     */
    private int httpGet(String url, int timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            try (InputStream in = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream()) {
                if (in != null) {
                    // 消费响应体，避免连接挂起
                    //noinspection StatementWithEmptyBody
                    while (in.read() != -1) {
                    }
                }
            }
            return code;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 构造待发布的客户主数据报文（取自 cmd_customer 生效中的客户）
     */
    private Map<String, Object> buildPayload(int count) {
        List<CmdCustomer> list = customerMapper.lambda()
            .eq(CmdCustomer::getStatus, "active")
            .orderByDesc(CmdCustomer::getUpdateTime)
            .last("LIMIT " + Math.min(Math.max(count, 1), 50))
            .list();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CmdCustomer c : list) {
            if (c == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("oneId", c.getOneId());
            m.put("legalName", c.getLegalName());
            m.put("creditCode", c.getCreditCode());
            m.put("customerType", c.getCustomerType());
            m.put("buScope", c.getBuScope());
            m.put("status", c.getStatus());
            rows.add(m);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("publishTime", LocalDateTime.now().toString());
        payload.put("bizType", "CUSTOMER");
        payload.put("total", rows.size());
        payload.put("customers", rows);
        return payload;
    }

    /**
     * 落一条运行记录
     */
    private void writeRun(IntEndpoint endpoint, String runCode, String status, int count, String message, int attempt) {
        IntRun run = new IntRun();
        run.setRunCode(runCode);
        run.setEndpointCode(endpoint.getEndpointCode());
        run.setEndpointName(endpoint.getEndpointName());
        run.setDirection(StringUtils.isBlank(endpoint.getDirection()) ? "OUTBOUND" : endpoint.getDirection());
        run.setTargetSystem(endpoint.getTargetSystem());
        run.setBizType(endpoint.getBizType());
        run.setTriggerType(attempt > 1 ? "RETRY" : "MANUAL");
        run.setRunStatus(status);
        run.setTotalCount(count);
        run.setSuccessCount("SUCCESS".equals(status) ? count : 0);
        run.setFailedCount("FAILED".equals(status) ? count : 0);
        run.setAttemptCount(attempt);
        run.setMaxAttempt(endpoint.getMaxRetry() == null ? 3 : endpoint.getMaxRetry());
        run.setErrorMessage("FAILED".equals(status) ? truncate(message) : null);
        run.setStartTime(LocalDateTime.now());
        run.setEndTime(LocalDateTime.now());
        runMapper.insert(run);
    }

    /**
     * 生成运行编号 RUN-yyyyMMdd-HHmmss-xxx
     */
    private String genRunCode() {
        return "RUN-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            + "-" + (int) (Math.random() * 900 + 100);
    }

    private int timeoutOf(IntEndpoint endpoint) {
        return endpoint.getTimeoutMs() == null || endpoint.getTimeoutMs() <= 0 ? 30000 : endpoint.getTimeoutMs();
    }

    /**
     * 轻量 HTTP POST（JDK 原生），2xx 视为成功，其余抛异常
     */
    private String httpPost(String url, String json, int timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return readBody(conn.getInputStream());
            }
            throw new IOException("HTTP " + code + ": " + StringUtils.defaultString(readBody(conn.getErrorStream()), "目标系统响应异常"));
        } finally {
            conn.disconnect();
        }
    }

    private String readBody(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1500 ? message.substring(0, 1500) : message;
    }
}