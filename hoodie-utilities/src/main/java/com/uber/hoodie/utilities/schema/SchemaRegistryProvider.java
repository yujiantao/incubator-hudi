/*
 *  Copyright (c) 2017 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.uber.hoodie.utilities.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.hoodie.DataSourceUtils;
import com.uber.hoodie.common.util.TypedProperties;
import com.uber.hoodie.exception.HoodieIOException;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import org.apache.avro.Schema;
import org.apache.spark.api.java.JavaSparkContext;

/**
 * Obtains latest schema from the Confluent/Kafka schema-registry
 *
 * https://github.com/confluentinc/schema-registry
 */
public class SchemaRegistryProvider extends SchemaProvider {

  /**
   * Configs supported
   */
  public static class Config {

    private static final String SRC_SCHEMA_REGISTRY_URL_PROP = "hoodie.deltastreamer.schemaprovider.registry.url";
    private static final String TARGET_SCHEMA_REGISTRY_URL_PROP =
        "hoodie.deltastreamer.schemaprovider.registry.targetUrl";
  }

  private final Schema schema;
  private final Schema targetSchema;

  private static String fetchSchemaFromRegistry(String registryUrl) throws IOException {
    URL registry = new URL(registryUrl);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(registry.openStream());
    return node.get("schema").asText();
  }

  public SchemaRegistryProvider(TypedProperties props, JavaSparkContext jssc) {
    super(props, jssc);
    DataSourceUtils.checkRequiredProperties(props, Collections.singletonList(Config.SRC_SCHEMA_REGISTRY_URL_PROP));
    String registryUrl = props.getString(Config.SRC_SCHEMA_REGISTRY_URL_PROP);
    String targetRegistryUrl = props.getString(Config.TARGET_SCHEMA_REGISTRY_URL_PROP, registryUrl);
    try {
      this.schema = getSchema(registryUrl);
      if (!targetRegistryUrl.equals(registryUrl)) {
        this.targetSchema = getSchema(targetRegistryUrl);
      } else {
        this.targetSchema = schema;
      }
    } catch (IOException ioe) {
      throw new HoodieIOException("Error reading schema from registry :" + registryUrl, ioe);
    }
  }

  private static Schema getSchema(String registryUrl) throws IOException {
    return new Schema.Parser().parse(fetchSchemaFromRegistry(registryUrl));
  }

  @Override
  public Schema getSourceSchema() {
    return schema;
  }

  @Override
  public Schema getTargetSchema() {
    return targetSchema;
  }
}
