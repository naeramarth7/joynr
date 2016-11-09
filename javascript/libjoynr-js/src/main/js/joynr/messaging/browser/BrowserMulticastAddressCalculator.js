/*
 * #%L
 * %%
 * Copyright (C) 2011 - 2016 BMW Car IT GmbH
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

define("joynr/messaging/browser/BrowserMulticastAddressCalculator", [ "joynr/util/UtilInternal"
], function(Util) {

    /**
     * @constructor BrowserMulticastAddressCalculator
     * @param {Object}
     *            settings
     * @param {BrowserAddress}
     *            settings.globalAddress
     */
    var BrowserMulticastAddressCalculator = function BrowserMulticastAddressCalculator(settings) {
        Util.checkProperty(settings, "Object", "settings");
        Util.checkProperty(settings.globalAddress, "BrowserAddress", "settings.globalAddress");

        /**
         * Calculates the multicast address for the submitted joynr message
         * @function BrowserMulticastAddressCalculator#calculate
         *
         * @param {JoynrMessage}
         *            message
         * @return {Address} the multicast address
         */
        this.calculate = function calculate(message) {
            return settings.globalAddress;
        };
    };

    return BrowserMulticastAddressCalculator;

});