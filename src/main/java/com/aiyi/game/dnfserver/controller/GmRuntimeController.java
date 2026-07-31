package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.impl.GmRuntimeHealthService;
import com.aiyi.game.dnfserver.service.impl.GmRealtimePlayerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("api/v1/gm/runtime")
public class GmRuntimeController {

    @Resource
    private GmRuntimeHealthService runtimeHealthService;
    @Resource
    private GmRealtimePlayerService realtimePlayerService;

    @GetMapping("health")
    public Map<String, Object> health() {
        return runtimeHealthService.health();
    }

    @GetMapping("online")
    public Map<String, Object> online(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "0") int offset,
                                      @RequestParam(defaultValue = "100") int limit) {
        return realtimePlayerService.online(keyword, offset, limit);
    }
}
