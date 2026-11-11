package com.dodosupermarket.im.controller;

import com.dodosupermarket.im.exception.ImException;
import com.dodosupermarket.im.service.ImAccountService;
import com.dodosupermarket.im.service.ImImageService;
import com.dodosupermarket.im.service.ImMessageContent;
import com.dodosupermarket.im.service.ImSendService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pers.binaryhunter.framework.bean.vo.R;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * IM 客服会话 REST 接口入口。
 * <p>对外只暴露会话生命周期与消息收发,内部依赖:
 * <ul>
 *   <li>{@link ImAccountService} — 会话租约、租户校验、读序号、未读统计。</li>
 *   <li>{@link ImSendService} — 文本 / 卡片 / 图片消息下发与送达回执。</li>
 *   <li>{@link ImImageService} — 附件上传与下载。</li>
 *   <li>{@link ImMessageContent} — 卡片选择器与引用摘要生成。</li>
 * </ul>
 * 通过 {@code dudu.im.enabled} 开关,允许在同一进程内按部署启用。
 */
@RestController
@RequestMapping("/api/v1/im")
@ConditionalOnProperty(name = "dudu.im.enabled", havingValue = "true")
public class ImController {
    private final ImAccountService service;
    private final ImSendService sends;
    private final ImImageService images;
    private final ImMessageContent content;

    public ImController(ImAccountService service, ImSendService sends, ImImageService images, ImMessageContent content) {
        this.service = service;
        this.sends = sends;
        this.images = images;
        this.content = content;
    }

    /**
     * 申请会话租约(POST /session)。
     * <p>白名单字段:{@code platform/deviceId/clientVersion/conversationId},
     * 其它键视作客户端篡改,直接 400 拒绝,避免凭据外泄。
     */
    @PostMapping("/session")
    public R<Map<String, Object>> issue(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        service.owner(request.getSession(false));
        if (!body.keySet().stream().allMatch(k -> k.equals("platform") || k.equals("deviceId") || k.equals("clientVersion") || k.equals("conversationId")))
            throw new ImException(400, "IM_UNTRUSTED_IDENTITY");
        if (body.containsKey("conversationId") && !(body.get("conversationId") instanceof String))
            throw new ImException(400, "IM_INVALID_REQUEST");
        return R.toResponse(service.issue(request.getSession(false), string(body, "platform"), string(body, "deviceId"), string(body, "conversationId")));
    }

    /**
     * 撤销指定租约(DELETE /session/{id}),用于客户端登出或设备切换。
     */
    @DeleteMapping("/session/{id}")
    public R<Map<String, Object>> revoke(HttpServletRequest r, @PathVariable String id) {
        service.revoke(r.getSession(false), id);
        return R.toResponse(Map.of("revoked", true));
    }

    /**
     * 列出当前会话主体可访问的全部会话,含每条会话的最近已读序号。
     */
    @GetMapping("/conversations")
    public R<Map<String, Object>> list(HttpServletRequest r) {
        return R.toResponse(service.conversations(r.getSession(false)));
    }

    /**
     * 未读消息总数与逐会话分布,用于会话列表小红点。
     */
    @GetMapping("/unread")
    public R<Map<String, Object>> unread(HttpServletRequest r) {
        return R.toResponse(service.unread(r.getSession(false)));
    }

    /**
     * 发送消息(POST /conversations/{id}/send)。
     * <p>按 body 形状自动分发:
     * <ul>
     *   <li>type ∈ {3,4,5} + referenceId → 业务卡片(商品/订单/优惠券)。</li>
     *   <li>type = 2 + attachmentId → 图片。</li>
     *   <li>其余 → 普通文本,可选 replyTo 引用上一条消息。</li>
     * </ul>
     */
    @PostMapping("/conversations/{id}/send")
    public R<Map<String, Object>> send(HttpServletRequest r, @PathVariable String id, @RequestBody Map<String, Object> body) {
        service.owner(r.getSession(false));
        if (body.size() == 3 && body.containsKey("clientMsgNo") && body.containsKey("referenceId") && (Integer.valueOf(3).equals(body.get("type")) || Integer.valueOf(4).equals(body.get("type")) || Integer.valueOf(5).equals(body.get("type"))))
            return R.toResponse(sends.sendCard(r.getSession(false), id, string(body, "clientMsgNo"), (Integer) body.get("type"), string(body, "referenceId")));
        if (body.size() == 3 && body.containsKey("clientMsgNo") && body.containsKey("attachmentId") && Integer.valueOf(2).equals(body.get("type")))
            return R.toResponse(sends.sendImage(r.getSession(false), id, string(body, "clientMsgNo"), string(body, "attachmentId")));
        if ((body.size() != 2 && !(body.size() == 3 && body.get("replyTo") instanceof String && !string(body, "replyTo").isEmpty())) || !body.containsKey("clientMsgNo") || !body.containsKey("content"))
            throw new ImException(400, "IM_INVALID_REQUEST");
        return R.toResponse(sends.send(r.getSession(false), id, string(body, "clientMsgNo"), string(body, "content"), string(body, "replyTo")));
    }

    /**
     * 卡片选择器数据(GET /card-choices),按 type + 关键词返回最多 10 条候选。
     * 响应强制 {@code Cache-Control: private, no-store},避免业务卡片数据落共享缓存。
     */
    @GetMapping("/conversations/{id}/card-choices")
    public ResponseEntity<R<Map<String, Object>>> choices(HttpServletRequest r, @PathVariable String id, @RequestParam int type, @RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(R.toResponse(content.choices(r.getSession(false), id, type, search)));
    }

    /**
     * 按消息 ID 拉取卡片最新投影,用于在历史消息中点击重新加载详情。
     * 同样强制 {@code no-store},卡片内容包含金额或状态,不能被共享缓存保留。
     */
    @GetMapping("/conversations/{id}/messages/{message}/card")
    public ResponseEntity<R<Map<String, Object>>> card(HttpServletRequest r, @PathVariable String id, @PathVariable String message) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(R.toResponse(content.card(r.getSession(false), id, message)));
    }

    /**
     * 上传图片附件(multipart/form-data)。
     * IO 异常统一映射为 503,提示客户端存储暂不可用。
     */
    @PostMapping(value = "/conversations/{id}/attachments", consumes = "multipart/form-data")
    public R<Map<String, Object>> uploadImage(HttpServletRequest r, @PathVariable String id, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try (java.io.InputStream input = file.getInputStream()) {
            return R.toResponse(images.upload(r.getSession(false), id, input, file.getSize()));
        } catch (java.io.IOException e) {
            throw new ImException(503, "IM_ATTACHMENT_STORAGE_FAILED");
        }
    }

    /**
     * 下载图片附件,返回二进制字节流。
     * 强制 {@code no-store}、{@code nosniff} 与 {@code inline} disposition,
     * 防止浏览器按 HTML 渲染图片或缓存到本地历史记录。
     */
    @GetMapping("/conversations/{id}/attachments/{attachment}")
    public ResponseEntity<byte[]> image(HttpServletRequest r, @PathVariable String id, @PathVariable String attachment) {
        ImImageService.Download image = images.download(r.getSession(false), id, attachment);
        // Web 下载器需要读取文件名，仅暴露此响应头，沿用现有跨域来源和鉴权限制。
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .header("Content-Disposition", "inline; filename=\"image\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(image.mime())).body(image.bytes());
    }

    /**
     * 查询消息发送状态({@code sending / sent / failed}),客户端断网重连后用于回填 UI。
     */
    @GetMapping("/conversations/{id}/sends/{number}")
    public R<Map<String, Object>> sendStatus(HttpServletRequest r, @PathVariable String id, @PathVariable String number) {
        return R.toResponse(sends.status(r.getSession(false), id, number));
    }

    /**
     * 拉取历史消息,按 {@code cursor} 倒序分页(默认 50 条)。
     */
    @GetMapping("/conversations/{id}/messages")
    public R<Map<String, Object>> history(HttpServletRequest r, @PathVariable String id,
                                          @RequestParam(defaultValue = "0") long cursor, @RequestParam(defaultValue = "50") int limit) {
        return R.toResponse(service.history(r.getSession(false), id, cursor, limit));
    }

    /**
     * 上报已读序号,把会话阅读水位推进到 {@code lastReadMessageSeq}。
     * 序号非法或超过当前最大消息序号都会被拒绝。
     */
    @PostMapping("/conversations/{id}/read")
    public R<Map<String, Object>> read(HttpServletRequest r, @PathVariable String id, @RequestBody Map<String, Object> body) {
        service.owner(r.getSession(false));
        try {
            return R.toResponse(service.read(r.getSession(false), id, Long.parseLong(string(body, "lastReadMessageSeq"))));
        } catch (NumberFormatException e) {
            throw new ImException(400, "IM_INVALID_READ_SEQ");
        }
    }

    /**
     * IM 域异常统一处理:把 {@link ImException} 转为框架 {@code R} 响应,
     * 不同 HTTP 状态映射到不同响应码(401→2,403→4,其它→3),便于前端区分鉴权失败/越权/业务错误。
     */
    @ExceptionHandler(ImException.class)
    public ResponseEntity<R<Map<String, String>>> error(ImException e) {
        R<Map<String, String>> body = R.toResponse(Map.of("errorCode", e.errorCode), e.status == 401 ? 2 : e.status == 403 ? 4 : 3);
        body.setMessage(e.errorCode);
        return ResponseEntity.status(e.status).body(body);
    }

    /**
     * 参数类型不匹配 / JSON 解析失败等统一收敛为 400 IM_INVALID_REQUEST,
     * 避免 Spring 默认错误页泄露内部栈信息。
     */
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<R<Map<String, String>>> invalidRequest(Exception ignored) {
        return error(new ImException(400, "IM_INVALID_REQUEST"));
    }

    /**
     * body 字段取值工具:不是 String 视作缺失,统一返回空串,避免空指针扩散到下游 Service。
     */
    private static String string(Map<String, Object> body, String key) {
        return body.get(key) instanceof String ? (String) body.get(key) : "";
    }
}
