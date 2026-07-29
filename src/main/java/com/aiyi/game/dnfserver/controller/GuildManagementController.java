package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.impl.GuildManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("api/v1/gm/guilds")
public class GuildManagementController {

    @Resource
    private GuildManagementService guildManagementService;

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
                                          @RequestBody Map<String, Object> payload) {
        return guildManagementService.addMember(guildId, payload);
    }

    @PutMapping("{guildId}")
    public Map<String, Object> updateGuild(@PathVariable int guildId,
                                            @RequestBody Map<String, Object> payload) {
        return guildManagementService.updateGuild(guildId, payload);
    }

    @PutMapping("{guildId}/members/{characNo}/grade")
    public Map<String, Object> updateMemberGrade(@PathVariable int guildId,
                                                  @PathVariable int characNo,
                                                  @RequestBody Map<String, Object> payload) {
        return guildManagementService.updateMemberGrade(guildId, characNo, payload);
    }
}
