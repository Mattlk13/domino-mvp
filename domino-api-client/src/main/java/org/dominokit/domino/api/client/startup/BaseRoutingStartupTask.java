package org.dominokit.domino.api.client.startup;

import org.dominokit.domino.api.client.ClientApp;
import org.dominokit.domino.api.client.events.BaseRoutingAggregator;
import org.dominokit.domino.api.client.mvp.presenter.BaseClientPresenter;
import org.dominokit.domino.history.DominoHistory;
import org.dominokit.domino.history.TokenFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public abstract class BaseRoutingStartupTask implements ClientStartupTask, PresenterRoutingTask {

    private static final Logger LOGGER = Logger.getLogger(BaseRoutingStartupTask.class.getName());

    protected List<BaseRoutingAggregator> aggregators = new ArrayList<>();
    protected boolean enabled = true;
    protected BaseClientPresenter presenter;

    public BaseRoutingStartupTask(List<? extends BaseRoutingAggregator> aggregators) {
        this.aggregators.addAll(aggregators);
        aggregators.forEach(aggregator -> aggregator.init(state -> {
            onStateReady(state);
            resetRouting();
        }));
    }

    private void resetRouting() {
        aggregators.forEach(BaseRoutingAggregator::resetRoutingState);
    }

    protected void bindPresenter(BaseClientPresenter presenter) {
        presenter.setRoutingTask(this);
        this.presenter = presenter;
    }

    @Override
    public void execute() {
        ClientApp.make()
                .getHistory()
                .listen(getTokenFilter(), state -> {
                    if (isNull(presenter) || !presenter.isActivated()) {
                        doRoutingIfEnabled(state);
                    } else {
                        if (isReRouteActivated()) {
                            doRoutingIfEnabled(state);
                        }
                    }

                }, isRoutingOnce())
                .onDirectUrl(getStartupTokenFilter());
    }

    protected void doRoutingIfEnabled(DominoHistory.State state) {
        if (enabled) {
            aggregators.forEach(aggregator -> aggregator.completeRoutingState(state));
        } else {
            if (nonNull(presenter)) {
                presenter.onSkippedRouting();
            }
        }
    }

    protected abstract void onStateReady(DominoHistory.State state);

    protected abstract TokenFilter getTokenFilter();

    protected TokenFilter getStartupTokenFilter() {
        return getTokenFilter();
    }

    protected boolean isRoutingOnce() {
        return false;
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    @Override
    public void enable() {
        this.enabled = true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    protected boolean isReRouteActivated() {
        return false;
    }
}
