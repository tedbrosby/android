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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ThreadStateDataSeries implements DataSeries<CpuProfilerStage.ThreadState> {

  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub myClient;
  @NotNull
  private final Common.Session mySession;
  private final int myThreadId;

  public ThreadStateDataSeries(@NotNull CpuServiceGrpc.CpuServiceBlockingStub client, @NotNull Common.Session session, int tid) {
    myClient = client;
    mySession = session;
    myThreadId = tid;
  }

  @Override
  public List<SeriesData<CpuProfilerStage.ThreadState>> getDataForXRange(Range xRange) {
    // TODO Investigate if this is too slow. We can then have them share a common "series", and return a view to that series.
    ArrayList<SeriesData<CpuProfilerStage.ThreadState>> data = new ArrayList<>();

    long min = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
    long max = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
    GetThreadsResponse threads = myClient.getThreads(GetThreadsRequest.newBuilder()
                                                                      .setSession(mySession)
                                                                      .setStartTimestamp(min)
                                                                      .setEndTimestamp(max)
                                                                      .build());

    GetTraceInfoResponse traces = myClient.getTraceInfo(GetTraceInfoRequest.newBuilder()
                                                                           .setSession(mySession)
                                                                           .setFromTimestamp(min)
                                                                           .setToTimestamp(max)
                                                                           .build());

    for (GetThreadsResponse.Thread thread : threads.getThreadsList()) {
      if (thread.getTid() == myThreadId) {
        // Merges information from traces and samples:
        ArrayList<Double> captureTimes = new ArrayList<>(traces.getTraceInfoCount() * 2);
        for (TraceInfo traceInfo : traces.getTraceInfoList()) {
          if (traceInfo.getThreadsList().stream().anyMatch(t -> t.getTid() == myThreadId)) {
            captureTimes.add((double)TimeUnit.NANOSECONDS.toMicros(traceInfo.getFromTimestamp()));
            captureTimes.add((double)TimeUnit.NANOSECONDS.toMicros(traceInfo.getToTimestamp()));
          }
        }

        List<GetThreadsResponse.ThreadActivity> list = thread.getActivitiesList();
        int i = 0;
        int j = 0;
        boolean inCapture = false;
        GetThreadsResponse.State state = GetThreadsResponse.State.UNSPECIFIED;
        while (i < list.size()) {
          GetThreadsResponse.ThreadActivity activity = list.get(i);

          long timestamp = TimeUnit.NANOSECONDS.toMicros(activity.getTimestamp());
          long captureTime = j < captureTimes.size() ? captureTimes.get(j).longValue() : Long.MAX_VALUE;

          long time;
          if (captureTime < timestamp) {
            inCapture = !inCapture;
            time = captureTime;
            j++;
          }
          else {
            state = activity.getNewState();
            time = timestamp;
            i++;
          }
          // We shouldn't add an activity if capture has started before the first activity for the current thread.
          if (state != GetThreadsResponse.State.UNSPECIFIED) {
            data.add(new SeriesData<>(time, getState(state, inCapture)));
          }
        }
        while (j < captureTimes.size()) {
          inCapture = !inCapture;
          data.add(new SeriesData<>(captureTimes.get(j).longValue(), getState(state, inCapture)));
          j++;
        }
      }
    }
    return data;
  }

  private static CpuProfilerStage.ThreadState getState(GetThreadsResponse.State state, boolean captured) {
    switch (state) {
      case RUNNING:
        return captured ? CpuProfilerStage.ThreadState.RUNNING_CAPTURED : CpuProfilerStage.ThreadState.RUNNING;
      case DEAD:
        return captured ? CpuProfilerStage.ThreadState.DEAD_CAPTURED : CpuProfilerStage.ThreadState.DEAD;
      case SLEEPING:
        return captured ? CpuProfilerStage.ThreadState.SLEEPING_CAPTURED : CpuProfilerStage.ThreadState.SLEEPING;
      case WAITING:
        return captured ? CpuProfilerStage.ThreadState.WAITING_CAPTURED : CpuProfilerStage.ThreadState.WAITING;
      default:
        // TODO: Use colors that have been agreed in design review.
        return CpuProfilerStage.ThreadState.UNKNOWN;
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(ThreadStateDataSeries.class);
  }
}