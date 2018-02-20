/*
 * #%L
 * %%
 * Copyright (C) 2011 - 2017 BMW Car IT GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
#include "joynr/ObjectWithDecayTime.h"
#include <chrono>

namespace joynr
{

ObjectWithDecayTime::ObjectWithDecayTime(const TimePoint& decayTime) : decayTime(decayTime)
{
}

std::chrono::milliseconds ObjectWithDecayTime::getRemainingTtl() const
{
    return decayTime - TimePoint::now();
}

TimePoint ObjectWithDecayTime::getDecayTime() const
{
    return decayTime;
}

bool ObjectWithDecayTime::isExpired() const
{
    return TimePoint::now() > decayTime;
}

} // namespace joynr
