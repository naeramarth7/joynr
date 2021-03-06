/*jslint node: true */

/*
 * #%L
 * %%
 * Copyright (C) 2017 BMW Car IT GmbH
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

const Promise = require("bluebird").Promise;
const joynr = require("joynr");
const prettyLog = require("test-base").logging.prettyLog;

exports.implementation = {
    add(opArgs) {
        prettyLog("SystemIntegrationTestProvider.add(" + JSON.stringify(opArgs) + ") called");
        return Promise.resolve().then(() => {
            if (opArgs.addendA === undefined || opArgs.addendB === undefined) {
                throw new joynr.exceptions.ProviderRuntimeException(
                        {detailMessage: "add: invalid argument data"});
            } else {
                return {result: opArgs.addendA + opArgs.addendB};
            }
        });
    }
};
