package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.service.impl.GmAuthorizationService;
import com.aiyi.game.dnfserver.service.impl.GmInventoryService;
import com.aiyi.game.dnfserver.service.impl.GmCurrencyService;
import com.aiyi.game.dnfserver.service.impl.GmContractService;
import com.aiyi.game.dnfserver.service.impl.GmPlayerProfileService;
import com.aiyi.game.dnfserver.service.impl.GmTargetTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("api/v1/gm/players")
public class GmPlayerController {

    @Resource
    private GmPlayerProfileService profileService;
    @Resource
    private GmInventoryService inventoryService;
    @Resource
    private GmTargetTokenService targetTokenService;
    @Resource
    private GmCurrencyService currencyService;
    @Resource
    private GmContractService contractService;
    @Resource
    private GmAuthorizationService authorizationService;

    @GetMapping("{characNo}")
    public Map<String, Object> basic(@PathVariable int characNo) {
        return profileService.basic(characNo);
    }

    @PostMapping("{characNo}/target-token")
    public Map<String, Object> targetToken(@PathVariable int characNo) {
        AccountVO operator = authorizationService.requireHighestAdmin();
        long accountId = profileService.accountIdFor(characNo);
        GmTargetTokenService.TargetToken issued = targetTokenService.issue(
                operator.getUid(), accountId, characNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetToken", issued.getToken());
        result.put("accountId", issued.getAccountId());
        result.put("characNo", issued.getCharacNo());
        result.put("expiresAt", issued.getExpiresAt());
        return result;
    }

    @GetMapping("{characNo}/inventory")
    public Map<String, Object> inventory(@PathVariable int characNo) {
        profileService.accountIdFor(characNo);
        return inventoryService.snapshot(characNo);
    }

    @GetMapping("{characNo}/currency")
    public Map<String, Object> currency(@PathVariable int characNo) {
        return currencyService.snapshot(characNo);
    }

    @GetMapping("currency/options")
    public Object currencyOptions() {
        return currencyService.currencyOptions();
    }

    @PutMapping("{characNo}/currency/change")
    public Map<String, Object> changeCurrency(@PathVariable int characNo,
                                               @RequestBody Map<String, Object> payload,
                                               HttpServletRequest request) {
        return currencyService.change(request, characNo, payload);
    }

    @GetMapping("{characNo}/contracts")
    public Map<String, Object> contracts(@PathVariable int characNo) {
        return contractService.list(characNo);
    }

    @PutMapping("{characNo}/contracts")
    public Map<String, Object> changeContract(@PathVariable int characNo,
                                              @RequestBody Map<String, Object> payload,
                                              HttpServletRequest request) {
        return contractService.change(request, characNo, payload);
    }

    @GetMapping("{characNo}/mail-rewards")
    public Map<String, Object> mailRewards(@PathVariable int characNo) {
        return profileService.mailAndRewards(characNo);
    }

    @GetMapping("{characNo}/guild-activity")
    public Map<String, Object> guildActivity(@PathVariable int characNo) {
        return profileService.guildAndActivity(characNo);
    }

    @GetMapping("{characNo}/security-audit")
    public Map<String, Object> securityAudit(@PathVariable int characNo) {
        return profileService.securityAndAudit(characNo);
    }
}
