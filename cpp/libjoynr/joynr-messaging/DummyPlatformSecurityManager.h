/*
 * #%L
 * %%
 * Copyright (C) 2011 - 2015 BMW Car IT GmbH
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
#ifndef DUMMYPLATFORMSECURITYMANAGER_H_
#define DUMMYPLATFORMSECURITYMANAGER_H_

#include "joynr/IPlatformSecurityManager.h"
#include "joynr/joynrlogging.h"

namespace joynr
{

class DummyPlatformSecurityManager : public IPlatformSecurityManager
{
public:
    DummyPlatformSecurityManager();

    virtual ~DummyPlatformSecurityManager()
    {
    }

    virtual QString getCurrentProcessUserId();
    virtual JoynrMessage sign(JoynrMessage message);
    virtual bool validate(const JoynrMessage& message) const;
    virtual QByteArray encrypt(const QByteArray& unencryptedBytes);
    virtual QByteArray decrypt(const QByteArray& encryptedBytes);

private:
    static joynr_logging::Logger* logger;
};

} // namespace joynr
#endif // DUMMYPLATFORMSECURITYMANAGER_H_
