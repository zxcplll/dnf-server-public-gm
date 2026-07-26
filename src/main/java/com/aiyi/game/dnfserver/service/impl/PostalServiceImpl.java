package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.Sort;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.enums.OrderBy;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.entity.common.Item;
import com.aiyi.game.dnfserver.entity.common.ItemType;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.service.PostalService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 16:37
 */
@Service
public class PostalServiceImpl implements PostalService {

    /** postal.endurance uses the 0-100 equipment quality percentage in the 86 schema. */
    private static final int TOP_QUALITY_VALUE = 100;

    @Resource
    private PostalDao postalDao;

    @Resource
    private AccountDao accountDao;

    @Resource
    private PvfManager pvfManager;

    @Override
    public ResultPage<Postal> list(String account, Date start,
                                   Date end, Integer page,
                                   Integer pageSize) {
        WherePrams where = Method.createDefault();
        if (!StringUtils.isEmpty(account)){
            Integer id = accountDao.selectByAccount(account);
            if (null != id){
                where.and(Postal::getReceiveCharacNo, C.EQ, id);
            }
        }
        if (null != start){
            where.and(Postal::getOccTime, C.DE, start);
        }
        if (null != end){
            where.and(Postal::getOccTime, C.XE, end);
        }
        where.orderBy(Sort.of(Postal::getPostalId, OrderBy.DESC));
        return postalDao.list(where, page, pageSize);
    }

    @Override
    public void sendMail(Postal postal) {
        applyHighestGrade(postal);
        if (StringUtils.isEmpty(postal.getSendCharacName())) {
            postal.setSendCharacName("GM后台");
        }
        postalDao.add(postal);
    }

    private void applyHighestGrade(Postal postal) {
        if (postal == null || !postal.isHighestGrade() || postal.getItemId() <= 0 || pvfManager == null) {
            return;
        }
        try {
            Item item = pvfManager.findItem((int) postal.getItemId());
            if (item != null && item.getType() == ItemType.equipment) {
                postal.setEndurance(TOP_QUALITY_VALUE);
            }
        } catch (RuntimeException ignored) {
            // A stale PVF must not prevent ordinary mail from being sent.
        }
    }
}
