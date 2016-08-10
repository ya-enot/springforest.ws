package org.filatov.springforest.ws.service.endpoint.mapping;

public abstract class JaxWsExecution<I, O> {

	abstract O execute(I input);

	public <W> JaxWsExecution<I, W> combine(final JaxWsExecution<O, W> step) {
		if (!validStep(step)) {

		}
		return new JaxWsExecution<I, W>() {
			@Override
			W execute(I input) {
				return step.execute(JaxWsExecution.this.execute(input));
			}
		};
	}

	private boolean validStep(JaxWsExecution<?, ?> step) {
		return this != step;
	}
}
