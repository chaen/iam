/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare (INFN). 2016-2018
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
package it.infn.mw.iam.api.common;

import javax.annotation.Generated;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.google.common.base.Preconditions;

public class OffsetPageable implements Pageable {

  private final int offset;
  private final int count;

  public OffsetPageable(int offset, int count) {

    Preconditions.checkArgument(offset >= 0, "offset must be greater or equal to 0");
    Preconditions.checkArgument(count >= 1, "count must be a positive integer");

    this.offset = offset;
    this.count = count;
  }

  @Override
  public int getPageNumber() {

    return offset / count;
  }

  @Override
  public int getPageSize() {

    return count;
  }

  @Override
  public int getOffset() {

    return offset;
  }

  @Override
  public Sort getSort() {

    return null;
  }

  @Override
  public Pageable next() {

    return new OffsetPageable(offset + count, count);
  }

  @Override
  public Pageable previousOrFirst() {

    int newOffset = offset - count;
    if (newOffset < 0) {
      newOffset = 0;
    }

    return new OffsetPageable(newOffset, count);

  }

  @Override
  public Pageable first() {

    return new OffsetPageable(0, count);
  }

  @Override
  public boolean hasPrevious() {

    return offset > 0;
  }

  @Generated("eclipse")
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + count;
    result = prime * result + offset;
    return result;
  }

  @Generated("eclipse")
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    OffsetPageable other = (OffsetPageable) obj;
    if (count != other.count)
      return false;
    if (offset != other.offset)
      return false;
    return true;
  }

  @Override
  public String toString() {

    return "OffsetPageable [offset=" + offset + ", count=" + count + "]";
  }

}