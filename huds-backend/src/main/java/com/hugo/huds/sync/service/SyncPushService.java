package com.hugo.huds.sync.service;

import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:46 a.m.
 */
@Slf4j
@Transactional(readOnly = true)
@Service
public class SyncPushService {

//    public SyncPushResponse push(SyncPushMutationRequest syncPushRequest){
//        SyncPushResponse response = new SyncPushResponse();
//        return response;
//
//    }
}
