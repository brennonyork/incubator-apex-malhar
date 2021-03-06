/**
 * Copyright (C) 2015 DataTorrent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.lib.metric;

import java.io.Serializable;
import java.util.Collection;

import com.datatorrent.api.annotation.Name;

import com.datatorrent.common.metric.SingleMetricAggregator;

@Name("avg")
public class AvgAggregator implements SingleMetricAggregator, Serializable
{
  @Override
  public Object aggregate(Collection<Object> metricValues)
  {
    double sum = 0;

    for (Object value : metricValues) {
      sum += ((Number) value).doubleValue();
    }
    return sum / metricValues.size();
  }

  private static final long serialVersionUID = 201504081330L;
}
