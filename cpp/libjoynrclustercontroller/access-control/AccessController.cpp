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

#include "AccessController.h"

#include <tuple>

#include "LocalDomainAccessController.h"
#include "joynr/BroadcastSubscriptionRequest.h"
#include "joynr/ImmutableMessage.h"
#include "joynr/LocalCapabilitiesDirectory.h"
#include "joynr/Message.h"
#include "joynr/MulticastSubscriptionRequest.h"
#include "joynr/Request.h"
#include "joynr/SubscriptionRequest.h"
#include "joynr/serializer/Serializer.h"
#include "joynr/system/RoutingTypes/Address.h"
#include "joynr/types/DiscoveryEntry.h"
#include "libjoynrclustercontroller/ClusterControllerCallContext.h"
#include "libjoynrclustercontroller/ClusterControllerCallContextStorage.h"

namespace joynr
{

using namespace infrastructure;
using namespace infrastructure::DacTypes;
using namespace types;

//--------- InternalConsumerPermissionCallbacks --------------------------------

class AccessController::LdacConsumerPermissionCallback
        : public LocalDomainAccessController::IGetPermissionCallback
{

public:
    LdacConsumerPermissionCallback(
            AccessController& owningAccessController,
            std::shared_ptr<ImmutableMessage> message,
            const std::string& domain,
            const std::string& interfaceName,
            TrustLevel::Enum trustlevel,
            std::shared_ptr<IAccessController::IHasConsumerPermissionCallback> callback);

    // Callbacks made from the LocalDomainAccessController
    void permission(Permission::Enum permission) override;
    void operationNeeded() override;

private:
    AccessController& owningAccessController;
    std::shared_ptr<ImmutableMessage> message;
    std::string domain;
    std::string interfaceName;
    TrustLevel::Enum trustlevel;
    std::shared_ptr<IAccessController::IHasConsumerPermissionCallback> callback;

    bool convertToBool(Permission::Enum permission);
};

AccessController::LdacConsumerPermissionCallback::LdacConsumerPermissionCallback(
        AccessController& parent,
        std::shared_ptr<ImmutableMessage> message,
        const std::string& domain,
        const std::string& interfaceName,
        TrustLevel::Enum trustlevel,
        std::shared_ptr<IAccessController::IHasConsumerPermissionCallback> callback)
        : owningAccessController(parent),
          message(std::move(message)),
          domain(domain),
          interfaceName(interfaceName),
          trustlevel(trustlevel),
          callback(callback)
{
}

void AccessController::LdacConsumerPermissionCallback::permission(Permission::Enum permission)
{
    bool hasPermission = convertToBool(permission);

    if (!hasPermission) {
        JOYNR_LOG_ERROR(owningAccessController.logger(),
                        "Message {} to domain {}, interface {} from creator {} failed ACL check",
                        message->getId(),
                        domain,
                        interfaceName,
                        message->getCreator());
    }
    callback->hasConsumerPermission(hasPermission);
}

void AccessController::LdacConsumerPermissionCallback::operationNeeded()
{
    // we only support operation-level ACL for unencrypted messages

    assert(!message->isEncrypted());
    std::string operation;
    const std::string& messageType = message->getType();
    if (messageType == Message::VALUE_MESSAGE_TYPE_ONE_WAY()) {
        try {
            OneWayRequest request;
            joynr::serializer::deserializeFromJson(request, message->getUnencryptedBody());
            operation = request.getMethodName();
        } catch (const std::exception& e) {
            JOYNR_LOG_ERROR(logger(), "could not deserialize OneWayRequest - error {}", e.what());
        }
    } else if (messageType == Message::VALUE_MESSAGE_TYPE_REQUEST()) {
        try {
            Request request;
            joynr::serializer::deserializeFromJson(request, message->getUnencryptedBody());
            operation = request.getMethodName();
        } catch (const std::exception& e) {
            JOYNR_LOG_ERROR(logger(), "could not deserialize Request - error {}", e.what());
        }
    } else if (messageType == Message::VALUE_MESSAGE_TYPE_SUBSCRIPTION_REQUEST()) {
        try {
            SubscriptionRequest request;
            joynr::serializer::deserializeFromJson(request, message->getUnencryptedBody());
            operation = request.getSubscribeToName();

        } catch (const std::invalid_argument& e) {
            JOYNR_LOG_ERROR(
                    logger(), "could not deserialize SubscriptionRequest - error {}", e.what());
        }
    } else if (messageType == Message::VALUE_MESSAGE_TYPE_BROADCAST_SUBSCRIPTION_REQUEST()) {
        try {
            BroadcastSubscriptionRequest request;
            joynr::serializer::deserializeFromJson(request, message->getUnencryptedBody());
            operation = request.getSubscribeToName();

        } catch (const std::invalid_argument& e) {
            JOYNR_LOG_ERROR(logger(),
                            "could not deserialize BroadcastSubscriptionRequest - error {}",
                            e.what());
        }
    } else if (messageType == Message::VALUE_MESSAGE_TYPE_MULTICAST_SUBSCRIPTION_REQUEST()) {
        try {
            MulticastSubscriptionRequest request;
            joynr::serializer::deserializeFromJson(request, message->getUnencryptedBody());
            operation = request.getSubscribeToName();
        } catch (const std::invalid_argument& e) {
            JOYNR_LOG_ERROR(logger(),
                            "could not deserialize MulticastSubscriptionRequest - error {}",
                            e.what());
        }
    }

    if (operation.empty()) {
        JOYNR_LOG_ERROR(owningAccessController.logger(), "Could not deserialize request");
        callback->hasConsumerPermission(false);
        return;
    }

    // Get the permission for given operation
    Permission::Enum permission =
            owningAccessController.localDomainAccessController->getConsumerPermission(
                    message->getCreator(), domain, interfaceName, operation, trustlevel);

    bool hasPermission = convertToBool(permission);

    if (!hasPermission) {
        JOYNR_LOG_ERROR(owningAccessController.logger(),
                        "Message {} to domain {}, interface/operation {}/{} from creator {} failed "
                        "ACL check",
                        message->getId(),
                        domain,
                        interfaceName,
                        operation,
                        message->getCreator());
    }

    callback->hasConsumerPermission(hasPermission);
}

bool AccessController::LdacConsumerPermissionCallback::convertToBool(Permission::Enum permission)
{
    switch (permission) {
    case Permission::YES:
        return true;
    case Permission::ASK:
        assert(false && "Permission.ASK user dialog not yet implemented.");
        return false;
    case Permission::NO:
        return false;
    default:
        return false;
    }
}

//--------- AccessController ---------------------------------------------------

class AccessController::ProviderRegistrationObserver
        : public LocalCapabilitiesDirectory::IProviderRegistrationObserver
{
public:
    explicit ProviderRegistrationObserver(
            std::shared_ptr<LocalDomainAccessController> localDomainAccessController)
            : localDomainAccessController(localDomainAccessController)
    {
    }
    void onProviderAdd(const DiscoveryEntry& discoveryEntry) override
    {
        std::ignore = discoveryEntry;
        // Ignored
    }

    void onProviderRemove(const DiscoveryEntry& discoveryEntry) override
    {
        localDomainAccessController->unregisterProvider(
                discoveryEntry.getDomain(), discoveryEntry.getInterfaceName());
    }

private:
    std::shared_ptr<LocalDomainAccessController> localDomainAccessController;
};

AccessController::AccessController(
        std::shared_ptr<LocalCapabilitiesDirectory> localCapabilitiesDirectory,
        std::shared_ptr<LocalDomainAccessController> localDomainAccessController)
        : localCapabilitiesDirectory(localCapabilitiesDirectory),
          localDomainAccessController(localDomainAccessController),
          providerRegistrationObserver(
                  std::make_shared<ProviderRegistrationObserver>(localDomainAccessController)),
          whitelistParticipantIds()
{
    localCapabilitiesDirectory->addProviderRegistrationObserver(providerRegistrationObserver);
}

AccessController::~AccessController()
{
    localCapabilitiesDirectory->removeProviderRegistrationObserver(providerRegistrationObserver);
}

void AccessController::addParticipantToWhitelist(const std::string& participantId)
{
    whitelistParticipantIds.push_back(participantId);
}

bool AccessController::needsHasConsumerPermissionCheck(const ImmutableMessage& message) const
{
    if (util::vectorContains(whitelistParticipantIds, message.getRecipient())) {
        return false;
    }

    const std::string& messageType = message.getType();
    if (messageType == Message::VALUE_MESSAGE_TYPE_MULTICAST() ||
        messageType == Message::VALUE_MESSAGE_TYPE_PUBLICATION() ||
        messageType == Message::VALUE_MESSAGE_TYPE_REPLY() ||
        messageType == Message::VALUE_MESSAGE_TYPE_SUBSCRIPTION_REPLY()) {
        // reply messages don't need permission check
        // they are filtered by request reply ID or subscritpion ID
        return false;
    }

    // If this point is reached, checking is required
    return true;
}

bool AccessController::needsHasProviderPermissionCheck() const
{
    const ClusterControllerCallContext& callContext = ClusterControllerCallContextStorage::get();

    if (callContext.getIsValid()) {
        return !callContext.getIsInternalProviderRegistration();
    }

    return true;
}

void AccessController::hasConsumerPermission(
        std::shared_ptr<ImmutableMessage> message,
        std::shared_ptr<IAccessController::IHasConsumerPermissionCallback> callback)
{
    if (!needsHasConsumerPermissionCheck(*message)) {
        callback->hasConsumerPermission(true);
        return;
    }

    // Get the domain and interface of the message destination
    std::function<void(const types::DiscoveryEntry&)> lookupSuccessCallback =
            [message, this, callback](const types::DiscoveryEntry& discoveryEntry) {
        const std::string& participantId = message->getRecipient();
        if (discoveryEntry.getParticipantId() != participantId) {
            JOYNR_LOG_ERROR(
                    logger(), "Failed to get capabilities for participantId {}", participantId);
            callback->hasConsumerPermission(false);
            return;
        }

        std::string domain = discoveryEntry.getDomain();
        std::string interfaceName = discoveryEntry.getInterfaceName();

        // Create a callback object
        auto ldacCallback = std::make_shared<LdacConsumerPermissionCallback>(
                *this, message, domain, interfaceName, TrustLevel::HIGH, callback);

        // Try to determine permission without expensive message deserialization
        // For now TrustLevel::HIGH is assumed.

        const std::string& msgCreatorUid = message->getCreator();
        localDomainAccessController->getConsumerPermission(
                msgCreatorUid, domain, interfaceName, TrustLevel::HIGH, ldacCallback);
    };

    std::function<void(const joynr::exceptions::ProviderRuntimeException&)> lookupErrorCallback =
            [callback](const joynr::exceptions::ProviderRuntimeException& exception) {
        std::ignore = exception;
        callback->hasConsumerPermission(false);
    };
    localCapabilitiesDirectory->lookup(
            message->getRecipient(), lookupSuccessCallback, lookupErrorCallback);
}

bool AccessController::hasProviderPermission(const std::string& userId,
                                             TrustLevel::Enum trustLevel,
                                             const std::string& domain,
                                             const std::string& interfaceName)
{
    if (!needsHasProviderPermissionCheck()) {
        return true;
    }

    return localDomainAccessController->getProviderPermission(
                   userId, domain, interfaceName, trustLevel) == Permission::Enum::YES;
}

} // namespace joynr
