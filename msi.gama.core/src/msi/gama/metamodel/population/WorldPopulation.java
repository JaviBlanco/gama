package msi.gama.metamodel.population;

import java.util.*;
import msi.gama.kernel.experiment.ExperimentatorAgent;
import msi.gama.metamodel.agent.*;
import msi.gama.metamodel.shape.ILocation;
import msi.gama.metamodel.topology.IEnvironment;
import msi.gama.metamodel.topology.continuous.*;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.IList;
import msi.gaml.species.ISpecies;

public class WorldPopulation extends GamlPopulation {

	public WorldPopulation(final ISpecies expr) {
		super(null, expr);
	}

	@Override
	public IList<? extends IAgent> createAgents(final IScope scope, final int number,
		final List<Map<String, Object>> initialValues, final boolean isRestored)
		throws GamaRuntimeException {
		if ( size() == 0 ) {
			WorldAgent world = new WorldAgent(this);
			world.setIndex(0);
			agents.add(world);
			createVariablesFor(scope, agents, initialValues);
			// initialize the model environment
			IEnvironment modelEnv = scope.getSimulationScope().getModel().getModelEnvironment();
			modelEnv.initializeFor(scope);
			world.initializeLocationAndGeomtry(scope);
			topology = new ContinuousTopology(scope, world.getGeometry(), false);
		}
		return agents;
	}

	@Override
	public IAgent getAgent(final ILocation value) {
		return get(null, 0);
	}

	public void setHost(final ExperimentatorAgent agent) {
		host = agent;
	}

	@Override
	public void computeTopology(final IScope scope) throws GamaRuntimeException {
		topology = new AmorphousTopology();
	}

}