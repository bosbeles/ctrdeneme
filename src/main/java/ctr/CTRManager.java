package ctr;

import ctr.gui.CTRListener;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.DoubleStream;

@Data
@EqualsAndHashCode(of = "id")
@Log4j2
@ToString(onlyExplicitlyIncluded = true)
public class CTRManager {


    public enum CTRState {
        LISTENING, ESTABLISHED, FINAL, FAIL
    }

    public static final String[] ALL_CTR = {"A", "B", "C", "D", "E"};
    public static final Map<String, Integer> CTR_MAP = new ConcurrentHashMap<>();

    static {
        for (int i = 0; i < ALL_CTR.length; i++) {
            CTR_MAP.put(ALL_CTR[i], i);
        }
    }


    private List<CTRListener> listeners = new ArrayList<>();

    private ScheduledFuture<?> cmdTimeoutFuture;
    private ScheduledFuture<?> queryTimeoutFuture;
    private ScheduledFuture<?> infoTimeoutFuture;



    @ToString.Include private int id;
    private boolean multicast;
    private CTRNetwork network;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @ToString.Include private CTRState state = CTRState.LISTENING;

    @ToString.Include private String tRef;
    @ToString.Include private String myP;
    @ToString.Include private Set<String> myC;
    @ToString.Include private int nQuery;
    @ToString.Include private int nConflict;
    @ToString.Include private int nInfo;
    private final int maxQuery = 3;
    private final int maxConflict = 6;
    private final int maxInfo = 4;
    @ToString.Include private double[] weight = new double[CTR_MAP.size()];


    public CTRManager(int id) {
        this.id = id;
    }

    public void join(CTRNetwork network) {

        this.network = network;
        network.add(this);
        log.info("{} is joined to the network {}", id, network.getManagers());
    }

    public void leave(CTRNetwork network) {
        network.remove(this);
        reset(null, Collections.emptySet());
        this.network = null;
        log.info("{} has left the network {}", id, network.getManagers());
    }

    public void onReceive(CTR ctr) {
        scheduler.execute(() -> doOnReceive(ctr));

    }

    private void doOnReceive(CTR ctr) {
        log.info("{} onReceive {}", id, ctr);
        switch (ctr.getType()) {
            case QUERY:
                rcvQuery(ctr.getP(), ctr.getC());
                break;
            case INFO:
                rcvInfo(ctr.getP(), ctr.getC());
                break;
            case COMMAND:
                rcvCmd(ctr.getP());
                break;
            case REJECT:
                rcvRej();
                break;
            default:
                break;
        }
        notifyListeners();
    }

    public void send(CTR ctr) {
        log.info("{} sent {}", id, ctr);
        if (network != null) {
            scheduler.execute(() -> network.send(this, ctr));
        }
    }

    public synchronized void start(String myP, Collection<String> myC) {
        reset(myP, myC);
        scheduler.execute(this::initialize);
    }

    private void reset(String myP, Collection<String> myC) {
        this.myP = myP;
        this.myC = ConcurrentHashMap.newKeySet(myC.size());
        this.myC.addAll(myC);
        cancel(queryTimeoutFuture);
        cancel(infoTimeoutFuture);
        cancel(cmdTimeoutFuture);
    }

    private void cancel(ScheduledFuture<?> future) {
        if(future != null) {
            future.cancel(false);
        }
    }

    private String chooseT() {
        String highestRef = null;
        double highest = 0.0;
        for (int i = 0; i < weight.length; i++) {
            if (weight[i] >= highest) {
                highestRef = ALL_CTR[i];
                highest = weight[i];
            }
        }
        return highestRef;
    }

    private void reweight(Set<String> c) {
        Set<String> intersection = new HashSet<>(myC);
        intersection.retainAll(c);
        if (!intersection.isEmpty()) {
            for (int i = 0; i < weight.length; i++) {
                String ctr = ALL_CTR[i];
                if (capable(ctr) && !c.contains(ctr)) {
                    weight[i] = weight[i] / 2;
                }
            }
            double totalDecr = 1.0 - DoubleStream.of(weight).sum();
            for (String inter : intersection) {
                weight[CTR_MAP.get(inter)] += totalDecr / intersection.size();
            }
        }

    }

    private void breakTie(String p) {
        log.error("{} {}: BreakTie({}, {})", id, state,
                myP, p);
        if (myP.compareTo(p) < 0) {
            tRef = p;
        } else {
            tRef = myP;
        }
        log.error("{} {}: BreakTie({}, {}) => TRef = {}", id, state, myP, p, tRef);

    }

    private boolean capable(String p) {
        return myC.contains(p);
    }

    private boolean capableT(String t) {
        return t.equals(tRef);
    }


    private void rcvCmd(String t) {

        switch (state) {
            case LISTENING:
            case ESTABLISHED:
                if (capable(t)) {
                    log.info("{} {}: RcvCmd({}) Capable({})", id, state,  t, t);
                    tRef = t;
                    sendCmd(tRef);
                    log.error("{} {} {}", id, state, CTRState.FINAL);
                    state = CTRState.FINAL;
                } else {
                    log.info("{} {}: RcvCmd({}) Not Capable({})", id, state,  t, t);
                    log.error("{} {}: RcvCmd({}) Not Capable({}) alert.", id, state,t, t);
                    log.error("{} {} {}", id, state, CTRState.FAIL);
                    state = CTRState.FAIL;
                }
                break;
            case FINAL:
                if (!capableT(t)) {
                    sendRej();
                    log.error("{} {}: RcvCmd({}) {} != TRef = {} alert.", id, state,t, tRef);
                    log.error("{} {} {}", id, state, CTRState.FAIL);
                    state = CTRState.FAIL;
                }
                break;
            default:
                log.info("{} {}: RcvCmd({})", id, state,  t);
                break;
        }
        log.info("{}", this);
    }

    private void rcvInfo(String p, Set<String> c) {
        switch (state) {
            case LISTENING:
                if (capable(p)) {
                    log.info("{} {}: RcvInfo({}, {}), Capable({})", id, state,  p, c, p);
                    reweight(c);
                    tRef = p;
                    sendInfo(tRef, myC);
                    nInfo = 1;
                    log.error("{} {} {}", id, state, CTRState.ESTABLISHED);
                    state = CTRState.ESTABLISHED;
                } else {
                    log.info("{} {}: RcvInfo({}, {}), Not Capable({})", id, state,  p, c, p);
                    reweight(c);
                }
                break;
            case ESTABLISHED:
                if (p.equals(tRef) && nInfo < maxInfo) {
                    log.info("{} {}: RcvInfo({}, {}), {} = TRef, {} < MaxInfo", id, state,  p, c, p, nInfo);
                    reweight(c);
                    nInfo++;
                } else if (p.equals(tRef) && nInfo >= maxInfo) {
                    log.info("{} {}: RcvInfo({}, {}), {} = TRef, {} >= MaxInfo", id, state,  p, c, p, nInfo);
                    reweight(c);
                    tRef = p;
                    sendCmd(tRef);
                    log.error("{} {} {}", id, state, CTRState.FINAL);
                    state = CTRState.FINAL;
                } else if (!capable(p) && nConflict < maxConflict) {
                    log.info("{} {}: RcvInfo({}, {}), {} != TRef = {}, Not Capable({}), {} < MaxConflict", id, state,  p, c, p, tRef, p, nConflict);
                    reweight(c);
                    nConflict++;
                    nQuery = 0;
                    log.error("{} {} {}", id, state, CTRState.LISTENING);
                    state = CTRState.LISTENING;
                    setUpQueryTimeout();

                } else if (capable(p) && nConflict <= maxConflict / 2) {
                    log.info("{} {}: RcvInfo({}, {}), {} != TRef = {}, Capable({}), {} < MaxConflict/2", id, state,  p, c, p, tRef, p, nConflict);
                    reweight(c);
                    nConflict++;
                    nQuery = 0;
                    log.error("{} {} {}", id, state, CTRState.LISTENING);
                    state = CTRState.LISTENING;
                    setUpQueryTimeout();
                } else if (capable(p) && nConflict < maxConflict) {
                    log.info("{} {}: RcvInfo({}, {}), {} != TRef = {}, Capable({}), MaxConflict/2 < {} < MaxConflict", id, state,  p, c, p, tRef, p, nConflict);
                    reweight(c);
                    breakTie(p);
                } else {
                    log.info("{} {}: RcvInfo({}, {}), {} != TRef = {}, {} = MaxConflict", id, state,  p, c, p, tRef, nConflict);
                    log.info("{} {}: RcvInfo({}, {}), {} != TRef = {}, {} = MaxConflict Alert", id, state,  p, c, p, tRef, nConflict);

                    log.error("{} {} {}", id, state, CTRState.FAIL);
                    state = CTRState.FAIL;
                }
                break;
            case FINAL:
                if (!p.equals(tRef)) {
                    log.info("{} {}: RcvInfo({}, {}), {} != TRef = {}", id, state,  p, c, p, tRef);
                    sendCmd(tRef);
                }
                else {
                    log.info("{} {}: RcvInfo({}, {}), {} = TRef", id, state,  p, c, p);
                }
                break;
            default:
                log.info("{} {}: RcvInfo({}, {})", id, state,  p, c);
                break;
        }
        log.info("{}", this);
    }

    private void rcvRej() {
        log.info("{} {}: RcvRej", id, state);
        if (!multicast || state != CTRState.FINAL) {
            log.error("{} {} RcvRej Alert", id, state);
            log.error("{} {} {}", id, state, CTRState.FAIL);
            state = CTRState.FAIL;
        }
        log.info("{}", this);
    }

    private void rcvQuery(String p, Set<String> c) {
        log.info("{} {}: RcvQuery({}, {})", id, state,  p, c);
        switch (state) {
            case LISTENING:
            case ESTABLISHED:
                reweight(c);
                break;
            case FINAL:
                sendCmd(tRef);
                break;
            default:
                break;
        }
        log.info("{}", this);
    }

    private synchronized void queryTimeout() {
        if (state == CTRState.LISTENING) {
            if (nQuery < maxQuery) {
                log.info("{} {}: QueryTimeout, {} < MaxQuery", id, state, nQuery);
                myP = chooseT();
                sendQuery(myP, myC);
                nQuery++;
                log.info("{}", this);
            } else if (nQuery == maxQuery) {
                log.info("{} {}: QueryTimeout, {} = MaxQuery", id, state, nQuery);
                tRef = chooseT();
                sendInfo(tRef, myC);
                nInfo = 0;
                log.error("{} {} {}", id, state, CTRState.ESTABLISHED);
                state = CTRState.ESTABLISHED;
                log.info("{}", this);
            }
            notifyListeners();
        }

    }

    private synchronized void infoTimeout() {
        if (state == CTRState.ESTABLISHED) {
            log.info("{} {}: InfoTimeout", id, state);
            sendInfo(tRef, myC);
            log.info("{}", this);
            notifyListeners();
        }

    }

    private synchronized void cmdTimeout() {
        if (state == CTRState.FINAL) {
            log.info("{} {}: CmdTimeout", id, state);
            sendCmd(tRef);
            log.info("{}", this);
            notifyListeners();
        }
    }


    private synchronized void initialize() {
        weight = new double[CTR_MAP.size()];
        int nc = myC.size();
        for (String ref : myC) {
            if (ref.equals(myP)) {
                setWeight(ref, 1 - 0.1 * (nc - 1));
            } else {
                setWeight(ref, 0.1);
            }
        }
        nQuery = 0;
        nConflict = 0;
        nInfo = 0;
        tRef = null;
        state = CTRState.LISTENING;
        log.info("Started: {}", this);
        notifyListeners();
        queryTimeout();
    }

    private void setWeight(String ref, double value) {
        weight[CTR_MAP.get(ref)] = value;

    }

    private void sendInfo(String p, Set<String> c) {
        send(create(CTR.Type.INFO, p, c));
        cancel(infoTimeoutFuture);
        infoTimeoutFuture = scheduler.schedule(this::infoTimeout, 30, TimeUnit.SECONDS);
    }


    private void sendQuery(String p, Set<String> c) {
        send(create(CTR.Type.QUERY, p, c));
        setUpQueryTimeout();
    }

    private void setUpQueryTimeout() {
        int delay = 10;
        if (nQuery == 0 && nConflict > 0) {
            delay = ThreadLocalRandom.current().nextInt(5 * nConflict * 2);
        }
        cancel(queryTimeoutFuture);
        log.info("QueryTimeout will be run in {} secs.", delay);
        queryTimeoutFuture = scheduler.schedule(this::queryTimeout, delay, TimeUnit.SECONDS);
    }

    private void sendCmd(String t) {
        send(create(CTR.Type.COMMAND, t, myC));
        cancel(cmdTimeoutFuture);
        cmdTimeoutFuture = scheduler.schedule(this::cmdTimeout, 4, TimeUnit.MINUTES);
    }

    private void sendRej() {
        send(create(CTR.Type.REJECT, myP, myC));
    }

    private CTR create(CTR.Type type, String p, Set<String> c) {
        CTR ctr = new CTR();
        ctr.setId(this.id);
        ctr.setType(type);
        ctr.setP(p);
        Set<String> copy = ConcurrentHashMap.newKeySet();
        copy.addAll(c);
        ctr.setC(copy);
        return ctr;
    }

    public void addListener(CTRListener listener) {
        this.listeners.add(listener);
    }

    public void notifyListeners() {
        for (CTRListener listener : listeners) {
            listener.onChange();
        }
    }


}
