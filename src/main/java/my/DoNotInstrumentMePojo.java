package my;

import java.util.ArrayList;
import java.util.List;

import org.drools.core.phreak.ReactiveList;
import org.drools.core.phreak.ReactiveObject;
import org.drools.core.phreak.ReactiveObjectUtil;
import org.drools.core.spi.Tuple;

public class DoNotInstrumentMePojo implements ReactiveObject {

    private String name;
    private int number;
    private List<String> asd;
    private List unused;

    public DoNotInstrumentMePojo(final String name, final int number) {
        this.$$_drools_write_name(name);
        this.$$_drools_write_number(number);
        this.$$_drools_write_asd(new ArrayList());
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.$$_drools_write_name(name);
    }

    public int getNumber() {
        return this.number;
    }

    public void setNumber(final int number) {
        this.$$_drools_write_number(number);
    }

    public void alt_setNumber(final int number) {
        System.out.println("Ciao");
        this.$$_drools_write_number(number);
    }

    public List<String> getAsd() {
        if (this.asd == null) {
            this.$$_drools_write_asd(new ArrayList());
        }
        return this.asd;
    }

    public void $$_drools_write_name(final String name) {
        this.name = name;
        ReactiveObjectUtil.notifyModification(this);
    }

    public void $$_drools_write_number(final int number) {
        this.number = number;
        ReactiveObjectUtil.notifyModification(this);
    }

    public void $$_drools_write_asd(final List list) {
        this.asd = (List<String>) new ReactiveList(list);
        System.out.println("That was a list");
    }

    private List<Tuple> $$_drools_lts;

    public void addLeftTuple(Tuple leftTuple) {
        if ($$_drools_lts == null) {
            $$_drools_lts = new ArrayList<Tuple>();
        }
        $$_drools_lts.add(leftTuple);
    }

    public List<Tuple> getLeftTuples() {
        return $$_drools_lts;
    }
}