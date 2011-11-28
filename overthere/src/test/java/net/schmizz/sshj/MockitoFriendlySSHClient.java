/* License added by: GRADLE-LICENSE-PLUGIN
 *
 * Copyright 2008-2011 XebiaLabs
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

package net.schmizz.sshj;

import java.io.IOException;

/**
 * Workaround for <a href="https://code.google.com/p/mockito/issues/detail?id=212" /> 
 */
public class MockitoFriendlySSHClient extends SSHClient {

    @Override
    public void connect(String hostname, int port) throws IOException {
        super.connect(hostname, port);
    }
}

