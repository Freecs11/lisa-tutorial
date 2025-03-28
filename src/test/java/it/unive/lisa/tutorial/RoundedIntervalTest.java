package it.unive.lisa.tutorial;

import it.unive.lisa.interprocedural.ReturnTopPolicy;
import org.junit.Test;

import it.unive.lisa.AnalysisException;
import it.unive.lisa.DefaultConfiguration;
import it.unive.lisa.LiSA;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.conf.LiSAConfiguration;
import it.unive.lisa.conf.LiSAConfiguration.GraphType;
import it.unive.lisa.imp.IMPFrontend;
import it.unive.lisa.imp.ParsingException;
import it.unive.lisa.program.Program;

public class RoundedIntervalTest {

	@Test
	public void testRoundedInterval() throws ParsingException, AnalysisException {
		// We parse the program to get the CFG representation of the code in it
		Program program = IMPFrontend.processFile("inputs/roundedinterval.imp");

		// We build a new configuration for the analysis
		LiSAConfiguration conf = new DefaultConfiguration();

		// We specify where we want files to be generated
		conf.workdir = "outputs/roundedinterval";

		// We specify the visual format of the analysis results
		conf.analysisGraphs = GraphType.HTML;

		// We specify the analysis that we want to execute
		conf.abstractState = DefaultConfiguration.simpleState(
				DefaultConfiguration.defaultHeapDomain(),
				new ValueEnvironment<>(new RoundedInterval()),
				DefaultConfiguration.defaultTypeDomain());

		conf.openCallPolicy = ReturnTopPolicy.INSTANCE;

		// We instantiate LiSA with our configuration
		LiSA lisa = new LiSA(conf);

		// Finally, we tell LiSA to analyze the program
		lisa.run(program);
	}
}