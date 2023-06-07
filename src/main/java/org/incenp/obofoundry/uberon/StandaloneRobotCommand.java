package org.incenp.obofoundry.uberon;

import org.obolibrary.robot.AnnotateCommand;
import org.obolibrary.robot.CollapseCommand;
import org.obolibrary.robot.CommandManager;
import org.obolibrary.robot.ConvertCommand;
import org.obolibrary.robot.DiffCommand;
import org.obolibrary.robot.ExpandCommand;
import org.obolibrary.robot.ExplainCommand;
import org.obolibrary.robot.ExportCommand;
import org.obolibrary.robot.ExportPrefixesCommand;
import org.obolibrary.robot.ExtractCommand;
import org.obolibrary.robot.FilterCommand;
import org.obolibrary.robot.MaterializeCommand;
import org.obolibrary.robot.MeasureCommand;
import org.obolibrary.robot.MergeCommand;
import org.obolibrary.robot.MirrorCommand;
import org.obolibrary.robot.PythonCommand;
import org.obolibrary.robot.QueryCommand;
import org.obolibrary.robot.ReasonCommand;
import org.obolibrary.robot.ReduceCommand;
import org.obolibrary.robot.RelaxCommand;
import org.obolibrary.robot.RemoveCommand;
import org.obolibrary.robot.RenameCommand;
import org.obolibrary.robot.RepairCommand;
import org.obolibrary.robot.ReportCommand;
import org.obolibrary.robot.TemplateCommand;
import org.obolibrary.robot.UnmergeCommand;
import org.obolibrary.robot.ValidateProfileCommand;
import org.obolibrary.robot.VerifyCommand;

/**
 * This class provides a version of the ROBOT tool that includes the commands
 * provided by this package as built-in commands. This is temporary until the
 * upstream ROBOT has support for pluggable commands.
 */
public class StandaloneRobotCommand {

    public static void main(String[] args) {
        CommandManager m = new CommandManager();
        m.addCommand("annotate", new AnnotateCommand());
        m.addCommand("collapse", new CollapseCommand());
        m.addCommand("convert", new ConvertCommand());
        m.addCommand("diff", new DiffCommand());
        m.addCommand("expand", new ExpandCommand());
        m.addCommand("explain", new ExplainCommand());
        m.addCommand("export", new ExportCommand());
        m.addCommand("export-prefixes", new ExportPrefixesCommand());
        m.addCommand("extract", new ExtractCommand());
        m.addCommand("filter", new FilterCommand());
        m.addCommand("materialize", new MaterializeCommand());
        m.addCommand("measure", new MeasureCommand());
        m.addCommand("merge", new MergeCommand());
        m.addCommand("mirror", new MirrorCommand());
        m.addCommand("python", new PythonCommand());
        m.addCommand("query", new QueryCommand());
        m.addCommand("reason", new ReasonCommand());
        m.addCommand("reduce", new ReduceCommand());
        m.addCommand("relax", new RelaxCommand());
        m.addCommand("remove", new RemoveCommand());
        m.addCommand("rename", new RenameCommand());
        m.addCommand("repair", new RepairCommand());
        m.addCommand("report", new ReportCommand());
        m.addCommand("template", new TemplateCommand());
        m.addCommand("unmerge", new UnmergeCommand());
        m.addCommand("validate-profile", new ValidateProfileCommand());
        m.addCommand("verify", new VerifyCommand());

        m.addCommand("merge-species", new MergeSpeciesCommand());
        m.addCommand("merge-equivalent-sets", new MergeEquivalentSetsCommand());

        m.main(args);
    }
}
