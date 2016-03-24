// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.domain.registry.dns.writer.api;

import com.google.common.base.Joiner;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link DnsWriter} that doesn't actually update records in a DNS server.
 *
 * <p>All this class does is write its displeasure to the logs.
 */
public final class VoidDnsWriter implements DnsWriter {

  private static final Logger logger = Logger.getLogger(VoidDnsWriter.class.getName());

  private final Set<String> names = new HashSet<>();

  @Override
  public void publishDomain(String domainName) {
    names.add(domainName);
  }

  @Override
  public void publishHost(String hostName) {
    names.add(hostName);
  }

  @Override
  public void close() {
    logger.warning("Ignoring DNS zone updates! No DnsWriterFactory implementation specified!\n"
        + Joiner.on('\n').join(names));
  }
}
