/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dummy implementation of {@link CpuServiceGrpc.CpuServiceImplBase}.
 * This class is used by the tests of the {@link com.android.tools.profilers.cpu} package.
 */
public class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

  /**
   * Real tid of the main thread of the trace.
   */
  public static final int TRACE_TID = 516;

  public static final int TOTAL_ELAPSED_TIME = 100;

  public static final int FAKE_TRACE_ID = 6;

  private CpuProfiler.CpuProfilingAppStartResponse.Status myStartProfilingStatus = CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS;

  private CpuProfiler.CpuProfilingAppStopResponse.Status myStopProfilingStatus = CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS;

  private Common.Session mySession;

  /**
   * Whether there is a valid trace in the getTraceInfo response.
   */
  private boolean myValidTrace;

  @Nullable
  private ByteString myTrace;

  private CpuCapture myCapture;

  private CpuProfiler.GetTraceResponse.Status myGetTraceResponseStatus;

  private int myAppTimeMs;

  private int mySystemTimeMs;

  private boolean myEmptyUsageData;

  private long myTraceThreadActivityBuffer;

  private boolean myIsAppBeingProfiled;

  private long myOngoingCaptureStartTimestamp;

  private CpuProfiler.TraceInitiationType myOngoingCaptureInitiationType;

  private int myTraceId = FAKE_TRACE_ID;

  private CpuProfiler.CpuProfilerType myProfilerType = CpuProfiler.CpuProfilerType.ART;

  private CpuProfiler.CpuProfilerConfiguration myProfilerConfiguration;

  private List<CpuProfiler.GetThreadsResponse.Thread> myAdditionalThreads = new ArrayList<>();

  private List<CpuProfiler.TraceInfo> myAdditionalTraceInfos = new ArrayList<>();

  private List<String> myTraceFilePaths = new ArrayList<>();

  /**
   * Session used in start/stop capturing gRPC requests in this fake service.
   */
  private Common.Session myStartStopCapturingSession;

  @Override
  public void startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest request,
                                StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> responseObserver) {
    CpuProfiler.CpuProfilingAppStartResponse.Builder response = CpuProfiler.CpuProfilingAppStartResponse.newBuilder();
    response.setStatus(myStartProfilingStatus);
    if (!myStartProfilingStatus.equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      response.setErrorMessage("StartProfilingApp error");
    } else {
      myProfilerConfiguration = request.getConfiguration();
      myIsAppBeingProfiled = true;
      myProfilerType = request.getConfiguration().getProfilerType();
      myOngoingCaptureInitiationType = CpuProfiler.TraceInitiationType.INITIATED_BY_UI;
    }
    myStartStopCapturingSession = request.getSession();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopProfilingApp(CpuProfiler.CpuProfilingAppStopRequest request,
                               StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> responseObserver) {
    CpuProfiler.CpuProfilingAppStopResponse.Builder response = CpuProfiler.CpuProfilingAppStopResponse.newBuilder();
    response.setStatus(myStopProfilingStatus);
    if (!myStopProfilingStatus.equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      response.setErrorMessage("StopProfilingApp error");
    } else {
      myIsAppBeingProfiled = false;
    }
    if (myValidTrace) {
      if (myTrace == null) {
        try {
          parseTraceFile();
        }
        catch (IOException | InterruptedException | ExecutionException ignored) {
        }
      }
      response.setTrace(myTrace);
      response.setTraceId(myTraceId);
    }
    myStartStopCapturingSession = request.getSession();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public CpuProfiler.CpuProfilerType getProfilerType() {
    return myProfilerType;
  }

  public void setProfilerType(CpuProfiler.CpuProfilerType profilerType) {
    myProfilerType = profilerType;
  }

  public void setTraceId(int id) {
    myTraceId = id;
  }

  @Override
  public void checkAppProfilingState(CpuProfiler.ProfilingStateRequest request,
                                     StreamObserver<CpuProfiler.ProfilingStateResponse> responseObserver) {
    CpuProfiler.ProfilingStateResponse.Builder response = CpuProfiler.ProfilingStateResponse.newBuilder();
    response.setBeingProfiled(myIsAppBeingProfiled);
    if (myIsAppBeingProfiled) {
      response.setConfiguration(myProfilerConfiguration).setStartTimestamp(myOngoingCaptureStartTimestamp)
              .setInitiationType(myOngoingCaptureInitiationType);
      myProfilerType = myProfilerConfiguration.getProfilerType();
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public void setAppBeingProfiled(boolean profiled) {
    myIsAppBeingProfiled = profiled;
  }

  /**
   * Receives a {@link CpuProfiler.CpuProfilerConfiguration} and sets the state of the service to be profiling using such configuration.
   * If the configuration passed is null, {@link #myIsAppBeingProfiled} should be set to false.
   */
  public void setOngoingCaptureConfiguration(@Nullable CpuProfiler.CpuProfilerConfiguration configuration, long startTimestamp) {
    setOngoingCaptureConfiguration(configuration, startTimestamp, CpuProfiler.TraceInitiationType.INITIATED_BY_UI);
  }

  public void setOngoingCaptureConfiguration(@Nullable CpuProfiler.CpuProfilerConfiguration configuration,
                                             long startTimestamp,
                                             @NotNull CpuProfiler.TraceInitiationType initiationType) {
    myProfilerConfiguration = configuration;
    myOngoingCaptureStartTimestamp = startTimestamp;
    myOngoingCaptureInitiationType = initiationType;
    myIsAppBeingProfiled = configuration != null;
  }

  public void setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status status) {
    myStartProfilingStatus = status;
  }

  public void setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status status) {
    myStopProfilingStatus = status;
  }

  public void setValidTrace(boolean validTrace) {
    myValidTrace = validTrace;
  }

  public void setTrace(@Nullable ByteString trace) {
    myTrace = trace;
  }

  @Override
  public void startMonitoringApp(CpuProfiler.CpuStartRequest request, StreamObserver<CpuProfiler.CpuStartResponse> responseObserver) {
    CpuProfiler.CpuStartResponse.Builder response = CpuProfiler.CpuStartResponse.newBuilder();
    mySession = request.getSession();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(CpuProfiler.CpuStopRequest request, StreamObserver<CpuProfiler.CpuStopResponse> responseObserver) {
    CpuProfiler.CpuStopResponse.Builder response = CpuProfiler.CpuStopResponse.newBuilder();
    mySession = request.getSession();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public Common.Session getSession() {
    return mySession;
  }

  public Common.Session getStartStopCapturingSession() {
    return myStartStopCapturingSession;
  }

  @Override
  public void getTraceInfo(CpuProfiler.GetTraceInfoRequest request, StreamObserver<CpuProfiler.GetTraceInfoResponse> responseObserver) {
    CpuProfiler.GetTraceInfoResponse.Builder response = CpuProfiler.GetTraceInfoResponse.newBuilder();
    if (myValidTrace) {
      Range requestRange = new Range(TimeUnit.NANOSECONDS.toMicros(request.getFromTimestamp()),
                                     TimeUnit.NANOSECONDS.toMicros(request.getToTimestamp()));
      if (myCapture == null) {
        try {
          parseTraceFile();
        }
        catch (IOException | ExecutionException | InterruptedException ignored) {
        }
        if (myCapture == null) {
          responseObserver.onNext(CpuProfiler.GetTraceInfoResponse.getDefaultInstance());
          responseObserver.onCompleted();
          return;
        }
      }
      boolean traceWithinRange = !myCapture.getRange().getIntersection(requestRange).isEmpty();
      if (traceWithinRange) {
        List<CpuProfiler.Thread> threads = new ArrayList<>();
        for (CpuThreadInfo threadInfo : myCapture.getThreads()) {
          threads.add(CpuProfiler.Thread.newBuilder()
                        .setTid(threadInfo.getId())
                        .setName(threadInfo.getName()).build());
        }

        CpuProfiler.TraceInfo traceInfo = CpuProfiler.TraceInfo.newBuilder()
                                                               .setTraceId(myTraceId)
                                                               .setFromTimestamp(
                                                                 TimeUnit.MICROSECONDS.toNanos((long)myCapture.getRange().getMin()))
                                                               .setToTimestamp(
                                                                 TimeUnit.MICROSECONDS.toNanos((long)myCapture.getRange().getMax()))
                                                               .setProfilerType(myCapture.getType())
                                                               .addAllThreads(threads)
                                                               .build();
        response.addTraceInfo(traceInfo);
      }
    }
    response.addAllTraceInfo(myAdditionalTraceInfos);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(CpuProfiler.CpuDataRequest request, StreamObserver<CpuProfiler.CpuDataResponse> responseObserver) {
    CpuProfiler.CpuDataResponse.Builder response = CpuProfiler.CpuDataResponse.newBuilder();
    if (!myEmptyUsageData) {
      // Add first usage data
      CpuProfiler.CpuUsageData.Builder cpuUsageData = CpuProfiler.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(0);
      cpuUsageData.setSystemCpuTimeInMillisec(0);
      cpuUsageData.setAppCpuTimeInMillisec(0);
      cpuUsageData.setEndTimestamp(0).build();
      response.addData(cpuUsageData);

      // Add second usage data.
      cpuUsageData = CpuProfiler.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(TOTAL_ELAPSED_TIME);
      cpuUsageData.setSystemCpuTimeInMillisec(mySystemTimeMs);
      cpuUsageData.setAppCpuTimeInMillisec(myAppTimeMs);
      cpuUsageData.setEndTimestamp(0).build();
      response.addData(cpuUsageData);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public void setAppTimeMs(int appTimeMs) {
    myAppTimeMs = appTimeMs;
  }

  public void setSystemTimeMs(int systemTimeMs) {
    mySystemTimeMs = systemTimeMs;
  }

  public void setEmptyUsageData(boolean emptyUsageData) {
    myEmptyUsageData = emptyUsageData;
  }

  public void addAdditionalThreads(int tid, String name, List<CpuProfiler.GetThreadsResponse.ThreadActivity> threads) {
    myAdditionalThreads.add(newThread(tid, name, threads));
  }

  public void addTraceInfo(CpuProfiler.TraceInfo infoList) {
    myAdditionalTraceInfos.add(infoList);
  }

  public void clearTraceInfo() {
    myAdditionalTraceInfos.clear();
  }

  @Override
  public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> responseObserver) {
    CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();
    List<CpuProfiler.GetThreadsResponse.Thread> threads = new ArrayList<>();
    if (myValidTrace) {
      try {
        threads.addAll(buildTraceThreads());
      } catch (InterruptedException | ExecutionException | IOException e) {
        threads.addAll(buildThreads(request.getStartTimestamp(), request.getEndTimestamp()));
      }
    } else {
      threads.addAll(buildThreads(request.getStartTimestamp(), request.getEndTimestamp()));
    }

    threads.addAll(myAdditionalThreads);
    response.addAllThreads(threads);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getTrace(CpuProfiler.GetTraceRequest request, StreamObserver<CpuProfiler.GetTraceResponse> responseObserver) {
    CpuProfiler.GetTraceResponse.Builder response = CpuProfiler.GetTraceResponse.newBuilder();
    response.setStatus(myGetTraceResponseStatus);
    if (myTrace != null) {
      response.setData(myTrace);
      response.setProfilerType(myProfilerType);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void saveTraceInfo(CpuProfiler.SaveTraceInfoRequest request, StreamObserver<CpuProfiler.EmptyCpuReply> responseObserver) {
    myTraceFilePaths.add(request.getTraceInfo().getTraceFilePath());
    responseObserver.onNext(CpuProfiler.EmptyCpuReply.getDefaultInstance());
    responseObserver.onCompleted();
  }

  public List<String> getTraceFilePaths() {
    return myTraceFilePaths;
  }

  /**
   * Create two threads that overlap for certain amount of time.
   * They are referred as thread1 and thread2 in the comments present in the tests.
   *
   * Thread1 is alive from 1s to 8s, while thread2 is alive from 6s to 15s.
   */
  private static List<CpuProfiler.GetThreadsResponse.Thread> buildThreads(long start, long end) {
    List<CpuProfiler.GetThreadsResponse.Thread> threads = new ArrayList<>();

    Range requestRange = new Range(start, end);

    Range thread1Range = new Range(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(8));
    if (!thread1Range.getIntersection(requestRange).isEmpty()) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread1 = new ArrayList<>();
      activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(1), CpuProfiler.GetThreadsResponse.State.RUNNING));
      activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(8), CpuProfiler.GetThreadsResponse.State.DEAD));
      threads.add(newThread(1, "Thread 1", activitiesThread1));
    }

    Range thread2Range = new Range(TimeUnit.SECONDS.toNanos(6), TimeUnit.SECONDS.toNanos(15));
    if (!thread2Range.getIntersection(requestRange).isEmpty()) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread2 = new ArrayList<>();
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(6), CpuProfiler.GetThreadsResponse.State.RUNNING));
      // Make sure we handle an unexpected state.
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(8), CpuProfiler.GetThreadsResponse.State.STOPPED));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(10), CpuProfiler.GetThreadsResponse.State.SLEEPING));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(12), CpuProfiler.GetThreadsResponse.State.WAITING));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(15), CpuProfiler.GetThreadsResponse.State.DEAD));
      threads.add(newThread(2, "Thread 2", activitiesThread2));
    }

    return threads;
  }

  /**
   * Create one thread with two activities: RUNNING ({@link myTraceThreadActivityBuffer} seconds before capture start)
   * and SLEEPING.
   */
  private List<CpuProfiler.GetThreadsResponse.Thread> buildTraceThreads() throws IOException, ExecutionException, InterruptedException {
    if (myCapture == null) {
      parseTraceFile();
    }
    Range range = myCapture.getRange();
    long rangeMid = (long)(range.getMax() + range.getMin()) / 2;

    List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
    activities.add(newActivity(TimeUnit.MICROSECONDS.toNanos(
      (long)range.getMin() + myTraceThreadActivityBuffer), CpuProfiler.GetThreadsResponse.State.RUNNING));
    activities.add(newActivity(TimeUnit.MICROSECONDS.toNanos(rangeMid), CpuProfiler.GetThreadsResponse.State.SLEEPING));

    return Collections.singletonList(newThread(TRACE_TID, "Trace tid", activities));
  }

  private static CpuProfiler.GetThreadsResponse.ThreadActivity newActivity(long timestampNs, CpuProfiler.GetThreadsResponse.State state) {
    CpuProfiler.GetThreadsResponse.ThreadActivity.Builder activity = CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder();
    activity.setNewState(state);
    activity.setTimestamp(timestampNs);
    return activity.build();
  }

  private static CpuProfiler.GetThreadsResponse.Thread newThread(
    int tid, String name, List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities) {
    CpuProfiler.GetThreadsResponse.Thread.Builder thread = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
    thread.setTid(tid);
    thread.setName(name);
    thread.addAllActivities(activities);
    return thread.build();
  }

  /**
   * Sets difference, in seconds, between the first thread activity and the trace capture start time.
   */
  public void setTraceThreadActivityBuffer(int seconds) {
    myTraceThreadActivityBuffer = TimeUnit.SECONDS.toMicros(seconds);
  }

  public void setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status getTraceResponseStatus) {
    myGetTraceResponseStatus = getTraceResponseStatus;
  }

  public CpuCapture parseTraceFile() throws IOException, ExecutionException, InterruptedException {
    if (myTrace == null) {
      myTrace = CpuProfilerTestUtils.readValidTrace();
    }
    if (myCapture == null) {
      myCapture = CpuProfilerTestUtils.getCapture(myTrace, myProfilerType);
    }
    return myCapture;
  }
}


