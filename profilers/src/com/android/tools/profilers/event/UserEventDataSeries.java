/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.event;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.KeyboardAction;
import com.android.tools.adtui.model.event.KeyboardData;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profilers.ProfilerClient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class UserEventDataSeries implements DataSeries<EventAction<UserEvent>> {

  @NotNull private ProfilerClient myClient;
  @NotNull private final Common.Session mySession;

  public UserEventDataSeries(@NotNull ProfilerClient client, @NotNull Common.Session session) {
    myClient = client;
    mySession = session;
  }

  @Override
  public List<SeriesData<EventAction<UserEvent>>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<EventAction<UserEvent>>> seriesData = new ArrayList<>();
    EventServiceGrpc.EventServiceBlockingStub eventService = myClient.getEventClient();
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()));
    EventProfiler.SystemDataResponse response = eventService.getSystemData(dataRequestBuilder.build());
    for (EventProfiler.SystemData data : response.getDataList()) {
      long actionStart = TimeUnit.NANOSECONDS.toMicros(data.getStartTimestamp());
      long actionEnd = TimeUnit.NANOSECONDS.toMicros(data.getEndTimestamp());
      switch (data.getType()) {
        case ROTATION:
          seriesData.add(new SeriesData<>(actionStart, new EventAction<>(actionStart, actionEnd, UserEvent.ROTATION)));
          break;
        case UNSPECIFIED:
          break;
        case TOUCH:
          seriesData.add(new SeriesData<>(actionStart, new EventAction<>(actionStart, actionEnd, UserEvent.TOUCH)));
          break;
        case KEY:
          seriesData.add(
            new SeriesData<>(actionStart, new KeyboardAction(actionStart, actionEnd, new KeyboardData(data.getEventData()))));
          break;
        case UNRECOGNIZED:
          break;
      }
    }
    return seriesData;
  }
}
