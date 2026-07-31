package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.entity.gm.GmOperationContext;
import com.aiyi.game.dnfserver.service.impl.GmAuthorizationService;
import com.aiyi.game.dnfserver.service.impl.GmOperationAuditService;
import com.aiyi.game.dnfserver.service.impl.GuildManagementService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("api/v1/gm/guilds")
public class GuildManagementController {

    @Resource
    private GuildManagementService guildManagementService;
    @Resource
    private GmAuthorizationService authorizationService;
    @Resource
    private GmOperationAuditService auditService;

    @GetMapping
    public Map<String, Object> listGuilds(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer guildId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return guildManagementService.listGuilds(keyword, guildId, page, pageSize);
    }

    @GetMapping("available-characters")
    public Map<String, Object> listAvailableCharacters(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return guildManagementService.listAvailableCharacters(keyword, page, pageSize);
    }

    @GetMapping("{guildId}/members")
    public Map<String, Object> listMembers(@PathVariable int guildId,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return guildManagementService.listMembers(guildId, keyword, page, pageSize);
    }

    @PostMapping("{guildId}/members")
    public Map<String, Object> addMember(@PathVariable int guildId,
                                          @RequestBody Map<String, Object> payload,
                                          HttpServletRequest request) {
        Integer characNo = optionalInt(payload == null ? null : payload.get("characNo"));
        return write(request, "ADD_MEMBER", guildId, characNo, payload,
                () -> guildManagementService.addMember(guildId, payload));
    }

    public Map<String, Object> addMember(int guildId, Map<String, Object> payload) {
        return guildManagementService.addMember(guildId, payload);
    }

    @PutMapping("{guildId}")
    public Map<String, Object> updateGuild(@PathVariable int guildId,
                                            @RequestBody Map<String, Object> payload,
                                            HttpServletRequest request) {
        return write(request, "UPDATE_GUILD", guildId, null, payload,
                () -> guildManagementService.updateGuild(guildId, payload));
    }

    public Map<String, Object> updateGuild(int guildId, Map<String, Object> payload) {
        return guildManagementService.updateGuild(guildId, payload);
    }

    @PutMapping("{guildId}/members/{characNo}/grade")
    public Map<String, Object> updateMemberGrade(@PathVariable int guildId,
                                                  @PathVariable int characNo,
                                                  @RequestBody Map<String, Object> payload,
                                                  HttpServletRequest request) {
        return write(request, "UPDATE_MEMBER_GRADE", guildId, characNo, payload,
                () -> guildManagementService.updateMemberGrade(guildId, characNo, payload));
    }

    public Map<String, Object> updateMemberGrade(int guildId, int characNo,
                                                  Map<String, Object> payload) {
        return guildManagementService.updateMemberGrade(guildId, characNo, payload);
    }

    @GetMapping("{guildId}/applications")
    public Map<String, Object> listApplications(@PathVariable int guildId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return guildManagementService.listApplications(guildId, page, pageSize);
    }

    @PostMapping("{guildId}/applications/{characNo}/approve")
    public Map<String, Object> approveApplication(@PathVariable int guildId,
                                                   @PathVariable int characNo,
                                                   HttpServletRequest request) {
        return write(request, "APPROVE_APPLICATION", guildId, characNo, null,
                () -> guildManagementService.approveApplication(guildId, characNo));
    }

    public Map<String, Object> approveApplication(int guildId, int characNo) {
        return guildManagementService.approveApplication(guildId, characNo);
    }

    @PostMapping("{guildId}/applications/{characNo}/reject")
    public Map<String, Object> rejectApplication(@PathVariable int guildId,
                                                  @PathVariable int characNo,
                                                  HttpServletRequest request) {
        return write(request, "REJECT_APPLICATION", guildId, characNo, null,
                () -> guildManagementService.rejectApplication(guildId, characNo));
    }

    public Map<String, Object> rejectApplication(int guildId, int characNo) {
        return guildManagementService.rejectApplication(guildId, characNo);
    }

    @GetMapping("{guildId}/announcement")
    public Map<String, Object> getAnnouncement(@PathVariable int guildId) {
        return guildManagementService.getAnnouncement(guildId);
    }

    @PutMapping("{guildId}/announcement")
    public Map<String, Object> updateAnnouncement(@PathVariable int guildId,
                                                   @RequestBody Map<String, Object> payload,
                                                   HttpServletRequest request) {
        return write(request, "UPDATE_ANNOUNCEMENT", guildId, null, payload,
                () -> guildManagementService.updateAnnouncement(guildId, payload));
    }

    public Map<String, Object> updateAnnouncement(int guildId, Map<String, Object> payload) {
        return guildManagementService.updateAnnouncement(guildId, payload);
    }

    @GetMapping("{guildId}/skills")
    public Map<String, Object> getSkills(@PathVariable int guildId) {
        return guildManagementService.getSkills(guildId);
    }

    @PutMapping("{guildId}/skills/points")
    public Map<String, Object> updateSkillPoints(@PathVariable int guildId,
                                                 @RequestBody Map<String, Object> payload,
                                                 HttpServletRequest request) {
        return write(request, "UPDATE_SKILL_POINTS", guildId, null, payload,
                () -> guildManagementService.updateSkillPoints(guildId, payload));
    }

    public Map<String, Object> updateSkillPoints(int guildId, Map<String, Object> payload) {
        return guildManagementService.updateSkillPoints(guildId, payload);
    }

    @PutMapping("{guildId}/members/{characNo}/contribution")
    public Map<String, Object> updateMemberContribution(@PathVariable int guildId,
                                                         @PathVariable int characNo,
                                                         @RequestBody Map<String, Object> payload,
                                                         HttpServletRequest request) {
        return write(request, "UPDATE_MEMBER_CONTRIBUTION", guildId, characNo, payload,
                () -> guildManagementService.updateMemberContribution(guildId, characNo, payload));
    }

    public Map<String, Object> updateMemberContribution(int guildId, int characNo,
                                                         Map<String, Object> payload) {
        return guildManagementService.updateMemberContribution(guildId, characNo, payload);
    }

    @DeleteMapping("{guildId}/members/{characNo}")
    public Map<String, Object> removeMember(@PathVariable int guildId,
                                            @PathVariable int characNo,
                                            HttpServletRequest request) {
        return write(request, "REMOVE_MEMBER", guildId, characNo, null,
                () -> guildManagementService.removeMember(guildId, characNo));
    }

    public Map<String, Object> removeMember(int guildId, int characNo) {
        return guildManagementService.removeMember(guildId, characNo);
    }

    private Map<String, Object> write(HttpServletRequest request,
                                      String action,
                                      int guildId,
                                      Integer targetCharacNo,
                                      Map<String, Object> body,
                                      GuildWrite write) {
        GmOperationContext context = authorizationService.authorize(
                request, "GUILD", action, null, targetCharacNo);
        Map<String, Object> auditPayload = auditPayload(guildId, targetCharacNo, body);
        GmOperationAuditService.AuditRecord audit = auditService.begin(context, auditPayload);
        if (audit.isReplay()) {
            return replay(audit);
        }
        try {
            Map<String, Object> result = write.apply();
            auditService.succeed(context.getRequestId(), auditPayload, result, result,
                    "DB_COMMITTED", "公会操作完成");
            return result;
        } catch (RuntimeException exception) {
            auditService.fail(context.getRequestId(), auditPayload, null, null,
                    "FAILED", compact(exception.getMessage()));
            throw exception;
        }
    }

    private Map<String, Object> auditPayload(int guildId,
                                             Integer targetCharacNo,
                                             Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (body != null) {
            result.putAll(body);
        }
        result.put("guildId", guildId);
        if (targetCharacNo != null) {
            result.put("characNo", targetCharacNo);
        }
        return result;
    }

    private Map<String, Object> replay(GmOperationAuditService.AuditRecord audit) {
        if (audit.getResponseJson() == null) {
            throw new ValidationException("相同请求正在处理中，请稍后查询审计状态");
        }
        JSONObject response = JSON.parseObject(audit.getResponseJson());
        return response == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(response);
    }

    private Integer optionalInt(Object value) {
        try {
            int result = value instanceof Number ? ((Number) value).intValue()
                    : Integer.parseInt(String.valueOf(value));
            return result > 0 ? result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String compact(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "公会操作失败";
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private interface GuildWrite {
        Map<String, Object> apply();
    }
}
