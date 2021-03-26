/*
 * Copyright © ${year} Dominokit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dominokit.domino.service.discovery.configuration;

import static java.util.Objects.isNull;

import io.vertx.core.json.JsonObject;

public class DataSourceServiceConfiguration extends BaseServiceConfiguration {
  private JsonObject location;

  public DataSourceServiceConfiguration(String name, JsonObject location) {
    super(name);
    if (isNull(location)) throw new NullServiceLocationException();
    this.location = location;
  }

  public JsonObject getLocation() {
    return location;
  }

  @Override
  public DataSourceServiceConfiguration metadata(JsonObject metadata) {
    this.metadata = metadata;
    return this;
  }

  public static class NullServiceLocationException extends RuntimeException {}
}
