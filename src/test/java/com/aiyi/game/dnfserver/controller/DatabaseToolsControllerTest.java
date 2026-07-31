package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.DataBaseExcute;
import com.aiyi.game.dnfserver.service.impl.GmAuthorizationService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DatabaseToolsControllerTest {

    @Test
    public void rawSqlEndpointIsDisabledByDefault() {
        AccountVODao accountVODao = mock(AccountVODao.class);
        GmAuthorizationService authorizationService = mock(GmAuthorizationService.class);
        DatabaseToolsController controller = controller(accountVODao, authorizationService, false);

        try {
            controller.excute(query("SELECT 1"));
            fail("Expected the raw SQL endpoint to be disabled");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("已关闭"));
        }

        verify(authorizationService, never()).requireHighestAdmin();
        verify(accountVODao, never()).listBySql(anyString());
        verify(accountVODao, never()).execute(anyString());
    }

    @Test
    public void enabledRawSqlEndpointStillRequiresHighestAdministrator() {
        AccountVODao accountVODao = mock(AccountVODao.class);
        GmAuthorizationService authorizationService = mock(GmAuthorizationService.class);
        when(authorizationService.requireHighestAdmin())
                .thenThrow(new ValidationException("只有最高管理员才能执行此操作"));
        DatabaseToolsController controller = controller(accountVODao, authorizationService, true);

        try {
            controller.excute(query("SELECT 1"));
            fail("Expected authorization to be required");
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("最高管理员"));
        }

        verify(accountVODao, never()).listBySql(anyString());
        verify(accountVODao, never()).execute(anyString());
    }

    @Test
    public void enabledEndpointExecutesDecodedSqlAfterAuthorization() {
        AccountVODao accountVODao = mock(AccountVODao.class);
        GmAuthorizationService authorizationService = mock(GmAuthorizationService.class);
        Map<String, Object> row = Collections.<String, Object>singletonMap("value", 1);
        when(accountVODao.listBySql("SELECT 1")).thenReturn(Collections.singletonList(row));
        DatabaseToolsController controller = controller(accountVODao, authorizationService, true);

        List<Map<String, Object>> result = controller.excute(query("SELECT 1"));

        assertEquals(Collections.singletonList(row), result);
        verify(authorizationService).requireHighestAdmin();
        verify(accountVODao).listBySql("SELECT 1");
    }

    private DatabaseToolsController controller(AccountVODao accountVODao,
                                               GmAuthorizationService authorizationService,
                                               boolean enabled) {
        DatabaseToolsController controller = new DatabaseToolsController();
        ReflectionTestUtils.setField(controller, "accountVODao", accountVODao);
        ReflectionTestUtils.setField(controller, "gmAuthorizationService", authorizationService);
        ReflectionTestUtils.setField(controller, "databaseToolsEnabled", enabled);
        return controller;
    }

    private DataBaseExcute query(String sql) {
        DataBaseExcute request = new DataBaseExcute();
        request.setType(0);
        request.setScript(Base64.getEncoder().encodeToString(sql.getBytes(StandardCharsets.UTF_8)));
        return request;
    }
}
