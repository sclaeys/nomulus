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

package com.google.domain.registry.util;

import com.google.common.base.Function;

import com.googlecode.objectify.Key;

/** Utilities for working with Objectify. */
public class ObjectifyUtils {

  public static final Function<Object, Key<?>> OBJECTS_TO_KEYS = new Function<Object, Key<?>>() {
    @Override
    public Key<?> apply(Object obj) {
      return Key.create(obj);
    }};
}
