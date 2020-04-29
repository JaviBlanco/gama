/*********************************************************************************************
 *
 *
 * 'Simulation.java', in plugin 'gama.core.headless', is part of the source code of the GAMA modeling and simulation
 * platform. (c) 2007-2014 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://code.google.com/p/gama-platform/ for license information and developers contact.
 *
 *
 **********************************************************************************************/
package gama.core.headless.job;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Vector;

import javax.imageio.ImageIO;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import gama.GAMA;
import gama.common.interfaces.IKeyword;
import gama.common.interfaces.IModel;
import gama.core.headless.common.DataType;
import gama.core.headless.common.Display2D;
import gama.core.headless.common.Globals;
import gama.core.headless.core.GamaHeadlessException;
import gama.core.headless.core.IRichExperiment;
import gama.core.headless.core.RichExperiment;
import gama.core.headless.core.RichOutput;
import gama.core.headless.runtime.RuntimeContext;
import gama.core.headless.xml.Writer;
import gama.core.headless.xml.XmlTAG;
import gama.dev.utils.DEBUG;
import gama.runtime.exceptions.GamaRuntimeException;
import gama.runtime.scope.IScope;
import gaml.GAML;
import gaml.descriptions.ExperimentDescription;
import gaml.descriptions.IDescription;
import gaml.descriptions.IExpressionDescription;
import gaml.expressions.IExpression;
import gaml.expressions.IExpressionFactory;
import gaml.operators.Cast;
import gaml.types.Types;

public class ExperimentJob implements IExperimentJob {

	private static long GLOBAL_ID_GENERATOR = 0;

	public enum OutputType {
		OUTPUT, EXPERIMENT_ATTRIBUTE, SIMULATION_ATTRIBUTE
	}

	public static class ListenedVariable {

		public class NA {
			NA() {}

			@Override
			public String toString() {
				return "NA";
			}
		}

		String name;
		public int width;
		public int height;
		int frameRate;
		OutputType type;
		DataType dataType;
		Object value;
		long step;
		String path;
		// private boolean isNa;

		private Object setNaValue() {
			this.value = new NA();
			// this.isNa = true;
			return this.value;
		}

		public ListenedVariable(final String name, final int width, final int height, final int frameRate,
				final OutputType type, final String outputPath) {
			this.name = name;
			this.width = width;
			this.height = height;
			this.frameRate = frameRate;
			this.type = type;
			this.path = outputPath;
			this.setNaValue();
		}

		public String getName() {
			return name;
		}

		public void setValue(final Object obj, final long st, final DataType typ) {
			// this.isNa = false;
			value = obj == null ? setNaValue() : obj;
			this.step = st;
			this.dataType = typ;
		}

		public void setValue(final Object obj, final long st) {
			setValue(obj, st, this.dataType);
		}

		public Object getValue() {
			return value;
		}

		public OutputType getType() {
			return type;
		}

		public DataType getDataType() {
			return dataType;
		}

		public String getPath() {
			return path;
		}
	}

	/**
	 * Variable listeners
	 */
	private ListenedVariable[] listenedVariables;
	private List<Parameter> parameters;
	private List<Output> outputs;
	private Writer outputFile;
	private String sourcePath;
	private String experimentName;
	private String modelName;
	private double seed;

	/**
	 * simulator to be loaded
	 */
	public IRichExperiment simulator;

	public IRichExperiment getSimulation() {
		return simulator;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	/**
	 * current step
	 */
	private long step;

	/**
	 * id of current experiment
	 */
	private String experimentID;
	public long finalStep;
	private String untilCond;
	IExpression endCondition;

	private static long generateID() {
		return ExperimentJob.GLOBAL_ID_GENERATOR++;
	}

	public void setBufferedWriter(final Writer w) {
		this.outputFile = w;
	}

	@Override
	public void addParameter(final Parameter p) {
		this.parameters.add(p);
	}

	@Override
	public void addOutput(final Output p) {
		p.setId("" + outputs.size());
		this.outputs.add(p);
	}

	private ExperimentJob() {
		initialize();

	}

	public ExperimentJob(final ExperimentJob clone) {
		this();
		this.experimentID = clone.experimentID != null ? clone.experimentID : "" + ExperimentJob.generateID();
		this.sourcePath = clone.sourcePath;
		this.finalStep = clone.finalStep;
		this.experimentName = clone.experimentName;
		this.modelName = clone.modelName;
		this.parameters = new ArrayList<>();
		this.outputs = new ArrayList<>();
		this.listenedVariables = clone.listenedVariables;
		this.step = clone.step;
		this.seed = clone.seed;
		for (final Parameter p : clone.parameters) {
			this.addParameter(new Parameter(p));
		}
		for (final Output o : clone.outputs) {
			this.addOutput(new Output(o));
		}

	}

	public ExperimentJob(final String sourcePath, final String exp, final long max, final String untilCond,
			final double s) {
		this(sourcePath, String.valueOf(ExperimentJob.generateID()), exp, max, untilCond, s);
	}

	public ExperimentJob(final String sourcePath, final String expId, final String exp, final long max,
			final String untilCond, final double s) {
		this();
		this.experimentID = expId;
		this.sourcePath = sourcePath;
		this.finalStep = max;
		this.untilCond = untilCond;
		this.experimentName = exp;
		this.seed = s;
		this.modelName = null;

	}

	@Override
	public void loadAndBuild(final RuntimeContext rtx) throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, IOException, GamaHeadlessException {

		this.load(rtx);
		this.listenedVariables = new ListenedVariable[outputs.size()];

		for (final Parameter temp : parameters) {
			if (temp.getName() == null || "".equals(temp.getName())) {
				this.simulator.setParameter(temp.getVar(), temp.getValue());
			} else {
				this.simulator.setParameter(temp.getName(), temp.getValue());
			}
		}
		this.setup();
		simulator.setup(experimentName, this.seed);
		for (int i = 0; i < outputs.size(); i++) {
			final Output temp = outputs.get(i);
			this.listenedVariables[i] = new ListenedVariable(temp.getName(), temp.getWidth(), temp.getHeight(),
					temp.getFrameRate(), simulator.getTypeOf(temp.getName()), temp.getOutputPath());
		}

		// Initialize the enCondition
		if (untilCond == null || "".equals(untilCond)) {
			endCondition = IExpressionFactory.FALSE_EXPR;
		} else {
			endCondition = GAML.compileExpression(untilCond, simulator.getSimulation(), true);
		}
		if (endCondition.getGamlType() != Types.BOOL)
			throw GamaRuntimeException.error("The until condition of the experiment should be a boolean",
					simulator.getSimulation().getScope());
	}

	public void load(final RuntimeContext ctx) throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, IOException, GamaHeadlessException {
		System.setProperty("user.dir", this.sourcePath);
		final IModel mdl = ctx.loadModel(new File(this.sourcePath));
		this.modelName = mdl.getName();
		this.simulator = new RichExperiment(mdl);
	}

	public void setup() {
		this.step = 0;

	}

	@Override
	public void playAndDispose() {
		final long startDate = Calendar.getInstance().getTimeInMillis();
		play();
		dispose();
		final long endDate = Calendar.getInstance().getTimeInMillis();
		System.out.println("\nSimulation duration: " + (endDate - startDate) + "ms");
		// DEBUG.OUT("\nSimulation duration: " + (endDate - startDate) + "ms");
	}

	@Override
	public void play() {
		if (this.outputFile != null) {
			this.outputFile.writeSimulationHeader(this);
		}
		// DEBUG.LOG("Simulation is running...", false);
		// System.out.println("Simulation is running...");
		// final long startdate = Calendar.getInstance().getTimeInMillis();
		final long affDelay = finalStep < 100 ? 1 : finalStep / 100;

		try {
			int step = 0;
			// Added because the simulation may be null in case we deal with a batch experiment
			IScope scope = GAMA.getRuntimeScope();
			while (!Cast.asBool(scope, endCondition.value(scope)) && (finalStep >= 0 ? step < finalStep : true)) {
				if (step % affDelay == 0) {
					DEBUG.LOG(".", false);
					System.out.print(".");
				}
				if (simulator.isInterrupted()) {
					break;
				}
				doStep();
				scope = GAMA.getRuntimeScope();
				step++;
			}
		} catch (final GamaRuntimeException e) {
			// DEBUG.ERR("\n The simulation has stopped before the end due to the following exception: ");
			System.out.println("\n The simulation has stopped before the end due to the following exception: ");
			e.printStackTrace();
		}
	}

	@Override
	public void dispose() {
		if (this.simulator != null) {
			this.simulator.dispose();
		}
		if (this.outputFile != null) {
			this.outputFile.close();
		}
	}

	@Override
	public void doStep() {
		this.step = simulator.step();
		this.exportVariables();
	}

	@Override
	public String getExperimentID() {
		return experimentID;
	}

	public void setExperimentID(final String experimentID) {
		this.experimentID = experimentID;
	}

	private void exportVariables() {
		final int size = this.listenedVariables.length;
		if (size == 0)
			return;
		for (int i = 0; i < size; i++) {
			final ListenedVariable v = this.listenedVariables[i];
			if (this.step % v.frameRate == 0) {
				final RichOutput out = simulator.getRichOutput(v);
				if (out == null || out.getValue() == null) {} else if (out.getValue() instanceof BufferedImage) {
					v.setValue(writeImageInFile((BufferedImage) out.getValue(), v.getName(), v.getPath()), step,
							out.getType());
				} else {
					v.setValue(out.getValue(), out.getStep(), out.getType());
				}
			} else {
				v.setValue(null, this.step);
			}
		}
		if (this.outputFile != null) {
			this.outputFile.writeResultStep(this.step, this.listenedVariables);
		}

	}

	public void initialize() {
		parameters = new Vector<>();
		outputs = new Vector<>();
		if (simulator != null) {
			simulator.dispose();
			simulator = null;
		}
		untilCond = "";
	}

	@Override
	public long getStep() {
		return step;
	}

	private Display2D writeImageInFile(final BufferedImage img, final String name, final String outputPath) {
		final String fileName = name + this.getExperimentID() + "-" + step + ".png";
		String fileFullName = Globals.IMAGES_PATH + "/" + fileName;
		if (outputPath != "" && outputPath != null) {
			// a specific output path has been specified with the "output_path"
			// keyword in the xml
			fileFullName = outputPath + "-" + step + ".png";
			// check if the folder exists, create a new one if it does not
			final File tmp = new File(fileFullName);
			tmp.getParentFile().mkdirs();
		}
		try {
			ImageIO.write(img, "png", new File(fileFullName));
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return new Display2D(name + this.getExperimentID() + "-" + step + ".png");
	}

	@Override
	public void setSeed(final double s) {
		this.seed = s;
	}

	@Override
	public double getSeed() {
		return this.seed;
	}

	@Override
	public Element asXMLDocument(final Document doc) {
		final Element simulation = doc.createElement(XmlTAG.SIMULATION_TAG);

		final Attr attr = doc.createAttribute(XmlTAG.ID_TAG);
		attr.setValue(this.experimentID);
		simulation.setAttributeNode(attr);

		final Attr attr3 = doc.createAttribute(XmlTAG.SOURCE_PATH_TAG);
		attr3.setValue(this.sourcePath);
		simulation.setAttributeNode(attr3);

		final Attr attr2 = doc.createAttribute(XmlTAG.FINAL_STEP_TAG);
		attr2.setValue(String.valueOf(this.finalStep));
		simulation.setAttributeNode(attr2);

		final Attr attr5 = doc.createAttribute(XmlTAG.SEED_TAG);
		attr5.setValue(String.valueOf(this.seed));
		simulation.setAttributeNode(attr5);

		final Attr attr4 = doc.createAttribute(XmlTAG.EXPERIMENT_NAME_TAG);
		attr4.setValue(this.experimentName);
		simulation.setAttributeNode(attr4);

		final Element parameters = doc.createElement(XmlTAG.PARAMETERS_TAG);
		simulation.appendChild(parameters);

		for (final Parameter p : this.parameters) {
			final Element aparameter = doc.createElement(XmlTAG.PARAMETER_TAG);
			parameters.appendChild(aparameter);

			final Attr ap1 = doc.createAttribute(XmlTAG.NAME_TAG);
			ap1.setValue(p.getName());
			aparameter.setAttributeNode(ap1);

			final Attr ap2 = doc.createAttribute(XmlTAG.VAR_TAG);
			ap2.setValue(p.getVar());
			aparameter.setAttributeNode(ap2);

			final Attr ap3 = doc.createAttribute(XmlTAG.TYPE_TAG);
			ap3.setValue(p.getType().toString());
			aparameter.setAttributeNode(ap3);

			final Attr ap4 = doc.createAttribute(XmlTAG.VALUE_TAG);
			ap4.setValue(p.getValue().toString());
			aparameter.setAttributeNode(ap4);
		}

		final Element outputs = doc.createElement(XmlTAG.OUTPUTS_TAG);
		simulation.appendChild(outputs);

		for (final Output o : this.outputs) {
			final Element aOutput = doc.createElement(XmlTAG.OUTPUT_TAG);
			outputs.appendChild(aOutput);

			final Attr o3 = doc.createAttribute(XmlTAG.ID_TAG);
			o3.setValue(o.getId());
			aOutput.setAttributeNode(o3);

			final Attr o1 = doc.createAttribute(XmlTAG.NAME_TAG);
			o1.setValue(o.getName());
			aOutput.setAttributeNode(o1);

			final Attr o2 = doc.createAttribute(XmlTAG.FRAMERATE_TAG);
			o2.setValue(o.getFrameRate().toString());
			aOutput.setAttributeNode(o2);
		}
		return simulation;
	}

	public static ExperimentJob loadAndBuildJob(final ExperimentDescription expD, final String path,
			final IModel model) {
		final String expName = expD.getName();
		final IExpressionDescription seedDescription = expD.getFacet(IKeyword.SEED);
		double mseed = 0.0;
		if (seedDescription != null) {
			mseed = Double.valueOf(seedDescription.getExpression().literalValue()).doubleValue();
			System.out.println("seed " + mseed);
		}
		final IDescription d = expD.getChildWithKeyword(IKeyword.OUTPUT);
		final ExperimentJob expJob =
				new ExperimentJob(path, String.valueOf(ExperimentJob.generateID()), expName, 0, "", mseed);

		if (d != null) {
			final Iterable<IDescription> monitors = d.getChildrenWithKeyword(IKeyword.MONITOR);
			for (final IDescription moni : monitors) {
				expJob.addOutput(Output.loadAndBuildOutput(moni));
			}

			final Iterable<IDescription> displays = d.getChildrenWithKeyword(IKeyword.DISPLAY);
			for (final IDescription disp : displays) {
				if (disp.getFacetExpr(IKeyword.VIRTUAL) != IExpressionFactory.TRUE_EXPR) {
					expJob.addOutput(Output.loadAndBuildOutput(disp));
				}
			}
		}

		final Iterable<IDescription> parameters = expD.getChildrenWithKeyword(IKeyword.PARAMETER);
		for (final IDescription para : parameters) {
			expJob.addParameter(Parameter.loadAndBuildParameter(para, model));
		}

		return expJob;
	}

	@Override
	public String getExperimentName() {

		return this.experimentName;
	}

	private Parameter getParameter(final String name) {
		for (final Parameter p : parameters) {
			if (p.getName().equals(name))
				return p;
		}
		return null;
	}

	@Override
	public List<Parameter> getParameters() {
		return this.parameters;
	}

	private Output getOutput(final String name) {
		for (final Output p : outputs) {
			if (p.getName().equals(name))
				return p;
		}
		return null;
	}

	@Override
	public List<Output> getOutputs() {
		return this.outputs;
	}

	@Override
	public void setParameterValueOf(final String name, final Object val) {
		this.getParameter(name).setValue(val);
	}

	@Override
	public void removeOutputWithName(final String name) {
		this.outputs.remove(this.getOutput(name));
	}

	@Override
	public void setOutputFrameRate(final String name, final int frameRate) {
		this.getOutput(name).setFrameRate(frameRate);
	}

	@Override
	public List<String> getOutputNames() {
		final List<String> res = new ArrayList<>();
		for (final Output o : outputs) {
			res.add(o.getName());
		}
		return res;
	}

	public long getFinalStep() {
		return finalStep;
	}

	@Override
	public void setFinalStep(final long finalStep) {
		this.finalStep = finalStep;
	}

	@Override
	public String getModelName() {
		return this.modelName;
	}

}
