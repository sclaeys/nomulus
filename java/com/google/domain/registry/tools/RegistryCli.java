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

package com.google.domain.registry.tools;

import static com.google.common.base.Preconditions.checkState;
import static com.google.domain.registry.tools.Injector.injectReflectively;

import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.model.ofy.ObjectifyService;
import com.google.domain.registry.tools.Command.RemoteApiCommand;
import com.google.domain.registry.tools.params.ParameterFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.HashMap;
import java.util.Map;

/** Container class to create and run remote commands against a datastore instance. */
@Parameters(separators = " =", commandDescription = "Command-line interface to the registry")
final class RegistryCli {

  @Parameter(
      names = {"-e", "--environment"},
      description = "Sets the default environment to run the command.")
  private RegistryToolEnvironment environment = RegistryToolEnvironment.PRODUCTION;

  @ParametersDelegate
  private AppEngineConnection connection = new AppEngineConnection();

  @ParametersDelegate
  private LoggingParameters loggingParams = new LoggingParameters();

  // The <? extends Class<? extends Command>> wildcard looks a little funny, but is needed so that
  // we can accept maps with value types that are subtypes of Class<? extends Command> rather than
  // literally that type (e.g. Class<? extends GtechCommand>).  For more explanation, see:
  //   http://www.angelikalanger.com/GenericsFAQ/FAQSections/TypeArguments.html#FAQ104
  void run(
      String programName,
      String[] args,
      ImmutableMap<String, ? extends Class<? extends Command>> commands) throws Exception {
    Security.addProvider(new BouncyCastleProvider());
    JCommander jcommander = new JCommander(this);
    jcommander.addConverterFactory(new ParameterFactory());
    jcommander.setProgramName(programName);

    // Store the instances of each Command class here so we can retrieve the same one for the
    // called command later on.  JCommander could have done this for us, but it doesn't.
    Map<String, Command> commandInstances = new HashMap<>();

    HelpCommand helpCommand = new HelpCommand(jcommander);
    jcommander.addCommand("help", helpCommand);
    commandInstances.put("help", helpCommand);

    for (Map.Entry<String, ? extends Class<? extends Command>> entry : commands.entrySet()) {
      Command command = entry.getValue().newInstance();
      jcommander.addCommand(entry.getKey(), command);
      commandInstances.put(entry.getKey(), command);
    }

    try {
      jcommander.parse(args);
    } catch (ParameterException e) {
      // If we failed to fully parse the command but at least found a valid command name, show only
      // the usage for that command. Otherwise, show full usage. Either way, rethrow the error.
      if (jcommander.getParsedCommand() == null) {
        jcommander.usage();
      } else {
        jcommander.usage(jcommander.getParsedCommand());
      }
      // Don't rethrow if we said: registry_tool command --help
      if ("Unknown option: --help".equals(e.getMessage())) {
        return;
      }
      throw e;
    }

    checkState(RegistryToolEnvironment.get() == environment,
        "RegistryToolEnvironment argument pre-processing kludge failed.");

    Command command = commandInstances.get(jcommander.getParsedCommand());
    if (command == null) {
      jcommander.usage();
      return;
    }
    loggingParams.configureLogging();  // Must be called after parameters are parsed.
    injectReflectively(RegistryToolComponent.class, DaggerRegistryToolComponent.create(), command);

    if (!(command instanceof RemoteApiCommand)) {
      command.run();
      return;
    }

    if (command instanceof ServerSideCommand) {
      ((ServerSideCommand) command).setConnection(connection);
    }

    // RemoteApiCommands need to have the remote api installed to work.
    RemoteApiInstaller installer = new RemoteApiInstaller();
    RemoteApiOptions options = new RemoteApiOptions();
    options.server(connection.getServer().getHostText(), connection.getServer().getPort());
    if (connection.isLocalhost()) {
      // Use dev credentials for localhost.
      options.useDevelopmentServerCredential();
    } else {
      options.useApplicationDefaultCredential();
    }
    installer.install(options);

    // Ensure that all entity classes are loaded before command code runs.
    ObjectifyService.initOfy();

    try {
      command.run();
    } finally {
      installer.uninstall();
    }
  }
}
