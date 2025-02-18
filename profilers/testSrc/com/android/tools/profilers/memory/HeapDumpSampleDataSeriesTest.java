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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class HeapDumpSampleDataSeriesTest {

  private final FakeMemoryService myService = new FakeMemoryService();

  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("HeapDumpSampleDataSeriesTest", myService);

  private MemoryProfilerStage myStage;

  @Before
  public void setUp() {
    myStage =
      new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), myIdeProfilerServices, new FakeTimer()),
                              new FakeCaptureObjectLoader());
  }

  @Test
  public void testGetDataForXRange() {
    HeapDumpInfo dumpInfo1 = HeapDumpInfo.newBuilder()
      .setStartTime(TimeUnit.MICROSECONDS.toNanos(2))
      .setEndTime(TimeUnit.MICROSECONDS.toNanos(7))
      .build();
    HeapDumpInfo dumpInfo2 = HeapDumpInfo.newBuilder()
      .setStartTime(TimeUnit.MICROSECONDS.toNanos(17))
      .setEndTime(Long.MAX_VALUE)
      .build();
    myService.addExplicitHeapDumpInfo(dumpInfo1);
    myService.addExplicitHeapDumpInfo(dumpInfo2);

    HeapDumpSampleDataSeries series =
      new HeapDumpSampleDataSeries(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA,
                                   myIdeProfilerServices.getFeatureTracker(), myStage);
    List<SeriesData<CaptureDurationData<CaptureObject>>> dataList =
      series.getDataForXRange(new Range(0, Double.MAX_VALUE));

    assertEquals(2, dataList.size());
    SeriesData<CaptureDurationData<CaptureObject>> data1 = dataList.get(0);
    assertEquals(2, data1.x);
    assertEquals(5, data1.value.getDurationUs());
    CaptureObject capture1 = data1.value.getCaptureEntry().getCaptureObject();
    assertEquals(TimeUnit.MICROSECONDS.toNanos(2), capture1.getStartTimeNs());
    assertEquals(TimeUnit.MICROSECONDS.toNanos(7), capture1.getEndTimeNs());

    SeriesData<CaptureDurationData<CaptureObject>> data2 = dataList.get(1);
    assertEquals(17, data2.x);
    assertEquals(Long.MAX_VALUE, data2.value.getDurationUs());
    CaptureObject capture2 = data2.value.getCaptureEntry().getCaptureObject();
    assertEquals(TimeUnit.MICROSECONDS.toNanos(17), capture2.getStartTimeNs());
    assertEquals(Long.MAX_VALUE, capture2.getEndTimeNs());
  }
}