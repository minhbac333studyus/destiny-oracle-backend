package com.destinyoracle.service;

import com.destinyoracle.dto.request.ActivityRequest;
import com.destinyoracle.dto.response.ActivityResponse;

import java.util.List;
import java.util.UUID;

public interface ActivityService {

    ActivityResponse logActivity(UUID userId, ActivityRequest request);

    List<ActivityResponse> getActivities(UUID userId, String aspectKey);
}
