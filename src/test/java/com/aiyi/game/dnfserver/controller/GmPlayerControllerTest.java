package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.service.impl.GmAuthorizationService;
import com.aiyi.game.dnfserver.service.impl.GmPlayerProfileService;
import com.aiyi.game.dnfserver.service.impl.GmTargetTokenService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GmPlayerControllerTest {

    @Test
    public void targetTokenIssuanceRequiresHighestAdministrator() {
        GmAuthorizationService authorizationService = mock(GmAuthorizationService.class);
        GmPlayerProfileService profileService = mock(GmPlayerProfileService.class);
        GmTargetTokenService targetTokenService = mock(GmTargetTokenService.class);
        when(authorizationService.requireHighestAdmin())
                .thenThrow(new ValidationException("只有最高管理员才能执行此操作"));
        GmPlayerController controller = new GmPlayerController();
        ReflectionTestUtils.setField(controller, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(controller, "profileService", profileService);
        ReflectionTestUtils.setField(controller, "targetTokenService", targetTokenService);

        try {
            controller.targetToken(23);
            fail("Expected target token issuance to require the highest administrator");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("最高管理员"));
        }

        verify(profileService, never()).accountIdFor(anyInt());
        verify(targetTokenService, never()).issue(anyLong(), anyLong(), anyInt());
    }
}
