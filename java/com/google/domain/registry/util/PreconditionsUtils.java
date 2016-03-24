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

import static com.google.common.base.Preconditions.checkArgument;

import javax.annotation.Nullable;

/** Utility methods related to preconditions checking. */
public class PreconditionsUtils {

  /**
   * Checks whether the provided reference is null, throws IAE if it is, and returns it if not.
   *
   * <p>This method and its overloads are to substitute for checkNotNull() in cases where it's
   * preferable to throw an IAE instead of an NPE, such as where we want an IAE to indicate that
   * it's just a bad argument/parameter and reserve NPEs for bugs and unexpected null values.
   */
  public static <T> T checkArgumentNotNull(T reference) {
    checkArgument(reference != null);
    return reference;
  }

  /** Checks whether the provided reference is null, throws IAE if it is, and returns it if not. */
  public static <T> T checkArgumentNotNull(T reference, @Nullable Object errorMessage) {
    checkArgument(reference != null, errorMessage);
    return reference;
  }

  /** Checks whether the provided reference is null, throws IAE if it is, and returns it if not. */
  public static <T> T checkArgumentNotNull(
      T reference, @Nullable String errorMessageTemplate, @Nullable Object... errorMessageArgs) {
    checkArgument(reference != null, errorMessageTemplate, errorMessageArgs);
    return reference;
  }
}
