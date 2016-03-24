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

package com.google.domain.registry.model;

import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.union;
import static com.google.domain.registry.util.CollectionUtils.difference;
import static com.google.domain.registry.util.CollectionUtils.nullToEmptyImmutableCopy;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppoutput.Response.ResponseData;
import com.google.domain.registry.model.ofy.CommitLogManifest;
import com.google.domain.registry.model.transfer.TransferData;

import com.googlecode.objectify.Ref;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

import org.joda.time.DateTime;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

/** An Epp entity object such as a contact or a host. */
@XmlTransient
public abstract class EppResource extends BackupGroupRoot implements Buildable, ResponseData {

  /**
   * Unique identifier in the registry for this resource.
   *
   * <p>This is in the (\w|_){1,80}-\w{1,8} format specified by RFC 5730 for roidType.
   */
  @Id
  @XmlElement(name = "roid")
  String repoId;

  /** The ID of the registrar that is currently sponsoring this resource. */
  @Index
  @XmlElement(name = "clID")
  String currentSponsorClientId;

  /** The ID of the registrar that created this resource. */
  @XmlElement(name = "crID")
  String creationClientId;

  /**
   * The ID of the registrar that last updated this resource.
   *
   * <p>This does not refer to the last delta made on this object, which might include out-of-band
   * edits; it only includes EPP-visible modifications such as {@literal <update>}. Can be null if
   * the resource has never been modified.
   */
  @XmlElement(name = "upID")
  String lastEppUpdateClientId;

  /** The time when this resource was created. */
  // Map the method to XML, not the field, because if we map the field (with an adaptor class) it
  // will never be omitted from the xml even if the timestamp inside creationTime is null and we
  // return null from the adaptor. (Instead it gets written as an empty tag.)
  @XmlTransient
  CreateAutoTimestamp creationTime = CreateAutoTimestamp.create(null);

  /**
   * The time when this resource was or will be deleted.
   *
   * <ul>
   *   <li>For deleted resources, this is in the past.
   *   <li>For pending-delete resources, this is in the near future.
   *   <li>For active resources, this is {@code END_OF_TIME}.
   * </ul>
   *
   * <p>This scheme allows for setting pending deletes in the future and having them magically drop
   * out of the index at that time, as long as we query for resources whose deletion time is before
   * now.
   */
  @Index
  @XmlTransient
  DateTime deletionTime;


  /**
   * The time that this resource was last updated.
   *
   * <p>This does not refer to the last delta made on this object, which might include out-of-band
   * edits; it only includes EPP-visible modifications such as {@literal <update>}. Can be null if
   * the resource has never been modified.
   */
  @XmlElement(name = "upDate")
  DateTime lastEppUpdateTime;

  /**
   * The time that this resource was last transferred.
   *
   * <p>Can be null if the resource has never been transferred.
   */
  // Map the method to XML, not the field, so subclasses can override it.
  @XmlTransient
  DateTime lastTransferTime;

  /** Status values associated with this resource. */
  Set<StatusValue> status;

  /** Data about any pending or past transfers on this contact. */
  @XmlTransient
  TransferData transferData;

  /**
   * Sorted map of {@link DateTime} keys (modified time) to {@link CommitLogManifest} entries.
   *
   * <p><b>Note:</b> Only the last revision on a given date is stored. The key is the transaction
   * timestamp, not midnight.
   *
   * @see com.google.domain.registry.model.translators.CommitLogRevisionsTranslatorFactory
   */
  @XmlTransient
  ImmutableSortedMap<DateTime, Ref<CommitLogManifest>> revisions = ImmutableSortedMap.of();

  public final String getRepoId() {
    return repoId;
  }

  @XmlElement(name = "crDate")
  public final DateTime getCreationTime() {
    return creationTime.getTimestamp();
  }

  public final String getCreationClientId() {
    return creationClientId;
  }

  public final DateTime getLastEppUpdateTime() {
    return lastEppUpdateTime;
  }

  public final String getLastEppUpdateClientId() {
    return lastEppUpdateClientId;
  }

  public final String getCurrentSponsorClientId() {
    return currentSponsorClientId;
  }

  public final ImmutableSet<StatusValue> getStatusValues() {
    return nullToEmptyImmutableCopy(status);
  }

  public final TransferData getTransferData() {
    return Optional.fromNullable(transferData).or(TransferData.EMPTY);
  }

  /** Returns whether there is any transferData. */
  public final boolean hasTransferData() {
    return transferData != null;
  }

  @XmlElement(name = "trDate")
  public DateTime getLastTransferTime() {
    return lastTransferTime;
  }

  public final DateTime getDeletionTime() {
    return deletionTime;
  }

  public ImmutableSortedMap<DateTime, Ref<CommitLogManifest>> getRevisions() {
    return nullToEmptyImmutableCopy(revisions);
  }

  /** Return a clone of the resource with timed status values modified using the given time. */
  public abstract EppResource cloneProjectedAtTime(DateTime now);

  /** Get the foreign key string for this resource. */
  public abstract String getForeignKey();

  /** Override of {@link Buildable#asBuilder} so that the extra methods are visible. */
  @Override
  public abstract Builder<?, ?> asBuilder();

  /** EppResources that are loaded via foreign keys should implement this marker interface. */
  public interface ForeignKeyedEppResource {}

  /** Abstract builder for {@link EppResource} types. */
  public abstract static class Builder<T extends EppResource, B extends Builder<?, ?>>
      extends GenericBuilder<T, B> {

    /** Create a {@link Builder} wrapping a new instance. */
    protected Builder() {}

    /** Create a {@link Builder} wrapping the given instance. */
    protected Builder(T instance) {
      super(instance);
    }

    /** Set the time this resource was created. Should only be used in tests. */
    @VisibleForTesting
    public B setCreationTimeForTest(DateTime creationTime) {
      getInstance().creationTime = CreateAutoTimestamp.create(creationTime);
      return thisCastToDerived();
    }

    /** Set the time after which this resource should be considered deleted. */
    public B setDeletionTime(DateTime deletionTime) {
      getInstance().deletionTime = deletionTime;
      return thisCastToDerived();
    }

    /** Set the current sponsoring registrar. */
    public B setCurrentSponsorClientId(String currentSponsorClientId) {
      getInstance().currentSponsorClientId = currentSponsorClientId;
      return thisCastToDerived();
    }

    /** Set the registrar that created this resource. */
    public B setCreationClientId(String creationClientId) {
      getInstance().creationClientId = creationClientId;
      return thisCastToDerived();
    }

    /** Set the time when a {@literal <update>} was performed on this resource. */
    public B setLastEppUpdateTime(DateTime lastEppUpdateTime) {
      getInstance().lastEppUpdateTime = lastEppUpdateTime;
      return thisCastToDerived();
    }

    /** Set the registrar who last performed a {@literal <update>} on this resource. */
    public B setLastEppUpdateClientId(String lastEppUpdateClientId) {
      getInstance().lastEppUpdateClientId = lastEppUpdateClientId;
      return thisCastToDerived();
    }

    /** Set the time when this resource was transferred. */
    public B setLastTransferTime(DateTime lastTransferTime) {
      getInstance().lastTransferTime = lastTransferTime;
      return thisCastToDerived();
    }

    /** Set this resource's status values. */
    public B setStatusValues(ImmutableSet<StatusValue> statusValues) {
      getInstance().status = statusValues;
      return thisCastToDerived();
    }

    /** Add to this resource's status values. */
    public B addStatusValue(StatusValue statusValue) {
      return addStatusValues(ImmutableSet.of(statusValue));
    }

    /** Remove from this resource's status values. */
    public B removeStatusValue(StatusValue statusValue) {
      return removeStatusValues(ImmutableSet.of(statusValue));
    }

    /** Add to this resource's status values. */
    public B addStatusValues(ImmutableSet<StatusValue> statusValues) {
      return setStatusValues(ImmutableSet.copyOf(
          union(getInstance().getStatusValues(), statusValues)));
    }

    /** Remove from this resource's status values. */
    public B removeStatusValues(ImmutableSet<StatusValue> statusValues) {
      return setStatusValues(ImmutableSet.copyOf(
          difference(getInstance().getStatusValues(), statusValues)));
    }

    /** Set this resource's transfer data. */
    public B setTransferData(TransferData transferData) {
      getInstance().transferData = transferData;
      return thisCastToDerived();
    }

    /** Set this resource's repoId. */
    public B setRepoId(String repoId) {
      getInstance().repoId = repoId;
      return thisCastToDerived();
    }

    /** Wipe out any personal information in the resource. */
    public B wipeOut() {
      return thisCastToDerived();
    }

    /** Build the resource, nullifying empty strings and sets and setting defaults. */
    @Override
    public T build() {
      // An EPP object has an implicit status of OK if no pending operations or prohibitions exist
      // (i.e. no other status value besides LINKED is present).
      removeStatusValue(StatusValue.OK);
      if (difference(getInstance().getStatusValues(), StatusValue.LINKED).isEmpty()) {
        addStatusValue(StatusValue.OK);
      }
      return buildWithoutImplicitStatusValues();
    }

    /** Build the resource, nullifying empty strings and sets and setting defaults. */
    public T buildWithoutImplicitStatusValues() {
      // If TransferData is totally empty, set it to null.
      if (TransferData.EMPTY.equals(getInstance().transferData)) {
        setTransferData(null);
      }
      // If there is no deletion time, set it to END_OF_TIME.
      setDeletionTime(Optional.fromNullable(getInstance().deletionTime).or(END_OF_TIME));
      return ImmutableObject.cloneEmptyToNull(super.build());
    }
  }
}
