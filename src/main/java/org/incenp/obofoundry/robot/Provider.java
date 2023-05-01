package org.incenp.obofoundry.robot;

import java.util.ArrayList;
import java.util.List;

import org.obolibrary.robot.Command;
import org.obolibrary.robot.ICommandProvider;

/**
 * Provider for all the pluggable ROBOT commands available in this package.
 */
public class Provider implements ICommandProvider {

    public List<Command> getCommands() {
        ArrayList<Command> cmds = new ArrayList<Command>();

        cmds.add(new HelloCommand());
        cmds.add(new MergeSpeciesCommand());

        return cmds;
    }

}
