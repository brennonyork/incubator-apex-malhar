/*
 * Copyright (c) 2015 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.contrib.dimensions;

import com.datatorrent.api.Context.OperatorContext;
import com.datatorrent.lib.appdata.schemas.*;

import com.datatorrent.lib.dimensions.DimensionsDescriptor;
import com.datatorrent.lib.dimensions.DimensionsEvent.Aggregate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.Serializable;
import javax.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;

import static com.datatorrent.lib.dimensions.AbstractDimensionsComputationFlexibleSingleSchema.DEFAULT_SCHEMA_ID;

/**
 * This is a dimensions store which stores data corresponding to one {@link DimensionalSchema} into an HDHT bucket.
 * This operator requires an upstream dimensions computation operator, to produce {@link Aggregate}s with the same
 * link {@link DimensionalSchema} and schemaID.
 *
 * @displayName Simple App Data Dimensions Store
 * @category Store
 * @tags appdata, dimensions, store
 */
public class AppDataSingleSchemaDimensionStoreHDHT extends AbstractAppDataDimensionStoreHDHT implements Serializable
{
  private static final long serialVersionUID = 201505130939L;
  /**
   * This is the id of the default bucket that data is stored into.
   */
  public static final long DEFAULT_BUCKET_ID = 0;
  /**
   * This is the JSON which defines this operator's {@link DimensionalConfigurationSchema}.
   */
  @NotNull
  private String configurationSchemaJSON;
  /**
   * This is the JSON which defines the schema stub for this operator's {@link DimensionalSchema}.
   */
  private String dimensionalSchemaStubJSON;
  /**
   * This operator's {@link DimensionalConfigurationSchema}.
   */
  @VisibleForTesting
  protected transient DimensionalConfigurationSchema configurationSchema;
  /**
   * This operator's {@link DimensionalSchema}.
   */
  protected transient DimensionalSchema dimensionalSchema;
  /**
   * The {@link schemaID} of for data stored by this operator.
   */
  private int schemaID = DEFAULT_SCHEMA_ID;
  /**
   * The ID of the HDHT bucket that this operator stores data in.
   */
  private long bucketID = DEFAULT_BUCKET_ID;
  /**
   * This flag determines whether or not the lists of all possible values for the keys in this operators {@link DimensionalSchema}
   * are updated based on the key values seen in {@link Aggregate}s received by this operator.
   */
  protected boolean updateEnumValues = false;
  @SuppressWarnings({"rawtypes"})
  /**
   * This is a map that stores the seen values of all the keys in this operator's {@link DimensionalSchema}. The
   * key in this map is the name of a key. The value in this map is the set of all values this operator has seen for
   * that key.
   */
  protected Map<String, Set<Comparable>> seenEnumValues;

  private Long minTimestamp = null;
  private Long maxTimestamp = null;

  @Override
  public void processEvent(Aggregate gae) {
    super.processEvent(gae);

    if(!dimensionalSchema.isPredefinedFromTo() &&
       gae.getKeys().getFieldDescriptor().getFields().getFields().contains(DimensionsDescriptor.DIMENSION_TIME)) {

      long timestamp = gae.getEventKey().getKey().getFieldLong(DimensionsDescriptor.DIMENSION_TIME);
      dimensionalSchema.setFrom(timestamp);

      if(minTimestamp == null || timestamp < minTimestamp) {
        minTimestamp = timestamp;
        dimensionalSchema.setFrom(minTimestamp);
      }

      if(maxTimestamp == null || timestamp > maxTimestamp) {
        maxTimestamp = timestamp;
        dimensionalSchema.setTo(maxTimestamp);
      }
    }

    if(updateEnumValues) {
      //update the lists of possible values for keys in this operator's {@link DimensionalSchema}.
      for(String field: gae.getKeys().getFieldDescriptor().getFields().getFields()) {
        if(DimensionsDescriptor.RESERVED_DIMENSION_NAMES.contains(field)) {
          continue;
        }

        @SuppressWarnings("rawtypes")
        Comparable fieldValue = (Comparable)gae.getKeys().getField(field);
        seenEnumValues.get(field).add(fieldValue);
      }
    }
  }

  @Override
  protected long getBucketKey(Aggregate event)
  {
    return bucketID;
  }

  @Override
  public void setup(OperatorContext context)
  {
    super.setup(context);

    this.buckets = Sets.newHashSet(bucketID);

    if(!dimensionalSchema.isPredefinedFromTo()) {
      if(minTimestamp != null) {
        dimensionalSchema.setFrom(minTimestamp);
      }

      if(maxTimestamp != null) {
        dimensionalSchema.setTo(minTimestamp);
      }
    }

    if(updateEnumValues) {
      if(seenEnumValues == null) {
        seenEnumValues = Maps.newHashMap();
        for(String key: configurationSchema.getKeyDescriptor().getFieldList()) {
          @SuppressWarnings("rawtypes")
          Set<Comparable> enumValuesSet = Sets.newHashSet();
          seenEnumValues.put(key, enumValuesSet);
        }
      }
    }
  }

  @Override
  protected SchemaRegistry getSchemaRegistry()
  {
    configurationSchema = new DimensionalConfigurationSchema(configurationSchemaJSON, aggregatorRegistry);
    dimensionalSchema = new DimensionalSchema(schemaID, dimensionalSchemaStubJSON, configurationSchema);

    return new SchemaRegistrySingle(dimensionalSchema);
  }

  @Override
  protected SchemaResult processSchemaQuery(SchemaQuery schemaQuery)
  {
    if(updateEnumValues) {
      //update the enum values in the schema.
      dimensionalSchema.setEnumsSetComparable(seenEnumValues);
    }

    return schemaRegistry.getSchemaResult(schemaQuery);
  }

  @Override
  public FieldsDescriptor getKeyDescriptor(int schemaID, int dimensionsDescriptorID)
  {
    return configurationSchema.getDimensionsDescriptorIDToKeyDescriptor().get(dimensionsDescriptorID);
  }

  @Override
  public FieldsDescriptor getValueDescriptor(int schemaID, int dimensionsDescriptorID, int aggregatorID)
  {
    return configurationSchema.getDimensionsDescriptorIDToAggregatorIDToOutputAggregatorDescriptor().get(dimensionsDescriptorID).get(aggregatorID);
  }

  @Override
  public long getBucketForSchema(int schemaID)
  {
    return bucketID;
  }

  /**
   * Sets the JSON representing the {@link DimensionalConfigurationSchema} for this operator.
   * @param configurationSchemaJSON The JSON representing the {@link DimensionalConfigurationSchema} for this operator.
   */
  public void setConfigurationSchemaJSON(String configurationSchemaJSON)
  {
    this.configurationSchemaJSON = configurationSchemaJSON;
  }

  /**
   * Sets the JSON representing the dimensional schema stub to be used by this operator's {@link DimensionalSchema}.
   * @param dimensionalSchemaStubJSON The JSON representing the dimensional schema stub to be used by this operator's {@link DimensionalSchema}.
   */
  public void setDimensionalSchemaStubJSON(String dimensionalSchemaStubJSON)
  {
    this.dimensionalSchemaStubJSON = dimensionalSchemaStubJSON;
  }

  /**
   * Returns the value of updateEnumValues.
   * @return The value of updateEnumValues.
   */
  public boolean isUpdateEnumValues()
  {
    return updateEnumValues;
  }

  /**
   * Sets the value of updateEnumValues. This value is true if the list of possible key values in this operator's {@link DimensionalSchema} is to be updated
   * based on observed values of the keys. This value is false if the possible key values in this operator's {@link DimensionalSchema}
   * are not to be updated.
   * @param updateEnumValues The value of updateEnumValues to set.
   */
  public void setUpdateEnumValues(boolean updateEnumValues)
  {
    this.updateEnumValues = updateEnumValues;
  }

  /**
   * Returns the schemaID of data stored by this operator.
   * @return The schemaID of data stored by this operator.
   */
  public int getSchemaID()
  {
    return schemaID;
  }

  /**
   * Sets the schemaId for the schema stored and served by this operator.
   * @param schemaID the schemaID to set
   */
  public void setSchemaID(int schemaID)
  {
    this.schemaID = schemaID;
  }

  /**
   * Gets the id of the bucket that this operator will store all its information in.
   * @return The id of the bucket that this operator will store all its information in.
   */
  public long getBucketID()
  {
    return bucketID;
  }

  /**
   * Sets the id of the bucket that this operator will store all its information in.
   * @param bucketID The id of the bucket that this operator will store all its information in.
   */
  public void setBucketID(long bucketID)
  {
    this.bucketID = bucketID;
  }
}
