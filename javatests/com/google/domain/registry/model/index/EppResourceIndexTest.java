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

package com.google.domain.registry.model.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.contact.ContactResource;

import com.googlecode.objectify.Key;

import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link EppResourceIndex}. */
public class EppResourceIndexTest extends EntityTestCase  {

  ContactResource contact;

  @Before
  public void setUp() throws Exception {
    createTld("tld");
    // The DatastoreHelper here creates the EppResourceIndex for us.
    contact = persistActiveContact("abcd1357");
  }

  @Test
  public void testPersistence() throws Exception {
    EppResourceIndex loadedIndex = Iterables.getOnlyElement(getEppResourceIndexObjects());
    assertThat(loadedIndex.reference.get()).isEqualTo(contact);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(Iterables.getOnlyElement(getEppResourceIndexObjects()), "kind");
  }

  @Test
  public void testIdempotentOnUpdate() throws Exception {
    contact = persistResource(contact.asBuilder().setEmailAddress("abc@def.fake").build());
    EppResourceIndex loadedIndex = Iterables.getOnlyElement(getEppResourceIndexObjects());
    assertThat(loadedIndex.reference.get()).isEqualTo(contact);
  }

  /**
   * Returns all EppResourceIndex objects across all buckets.
   */
  private ImmutableList<EppResourceIndex> getEppResourceIndexObjects() {
    int numBuckets = RegistryEnvironment.get().config().getEppResourceIndexBucketCount();
    ImmutableList.Builder<EppResourceIndex> indexEntities = ImmutableList.builder();
    for (int i = 0; i < numBuckets; i++) {
      indexEntities.addAll(ofy().load()
          .type(EppResourceIndex.class)
          .ancestor(Key.create(EppResourceIndexBucket.class, i + 1)));
    }
    return indexEntities.build();
  }
}
