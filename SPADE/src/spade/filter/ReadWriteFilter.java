/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

This program is free software: you can redistribute it and/or
modify it under the terms of the GNU General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
--------------------------------------------------------------------------------
 */

package spade.filter;

import spade.core.AbstractFilter;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class ReadWriteFilter extends AbstractFilter {

    /*

    boolean chain;
    FilterInterface chainFilter;
    ConsumerInterface storage;
    private HashMap<String, ReadWriteRuns> files;
    //files is basically hashmap from file path -> read/write runs for all processes

    public ReadWriteFilter(ConsumerInterface inputStorage) {

    chain = false;
    files = new HashMap<String, ReadWriteRuns>();
    storage = inputStorage;
    }

    public ReadWriteFilter(ConsumerInterface inputStorage, FilterInterface f) {

    chain = true;
    chainFilter = f;
    files = new HashMap<String, ReadWriteRuns>();
    storage = inputStorage;
    }

    @Override
    public boolean putVertex(Agent a) {
    if (chain == false) {
    storage.putVertex(a);
    } else {
    chainFilter.putVertex(a);
    }
    return true;

    }

    @Override
    public boolean putVertex(Process p) {
    if (chain == false) {
    storage.putVertex(p);
    } else {
    chainFilter.putVertex(p);
    }
    return true;

    }

    @Override
    public boolean putVertex(Artifact a) {
    if (chain == false) {
    storage.putVertex(a);
    } else {
    chainFilter.putVertex(a);
    }
    return true;

    }

    @Override
    public boolean putEdge(Used u) {
    Vertex v1 = u.getSrcVertex();
    Vertex v2 = u.getDstVertex();
    //first of all get the filename
    String filename = u.getArtifact().getAnnotationValue("filename");
    //get the pid of the reading process
    String pid = u.getProcess().getAnnotationValue("pid");

    //check if you are keeping state for this file already or not otherwise create state for this file
    if (files.containsKey(filename)) {
    //we have state for this file. so this is not a new file
    //get that state
    ReadWriteRuns state = (ReadWriteRuns) files.get(filename);

    //check if this is a new read or a read run
    if (state.getCurrentRun() == 0) {
    //continuing read


    //now check if we have state for this process

    if (state.getReadRuns().containsKey(pid)) {
    //this process already has a run with this file so update the read run for this process
    //update the read here!!! DO THIS HERE
    state.getReadRuns().get(pid).getAnnotations().put("endtime", u.getArtifact().getAnnotationValue("time"));






    } else {
    //process hasn't read the file yet so start a new run for it (done)
    state.getReadRuns().put(pid, u);
    state.getReadRuns().get(pid).getAnnotations().put("starttime", u.getArtifact().getAnnotationValue("time"));

    }


    } else {
    //new read. could be end of a write run. or just start of a fresh read run. check for both (done)

    //see if there are writes to flush
    if (state.getCurrentRun() == -1) {
    //first ever run no need to flush previous write runs
    state.setCurrentRun(0);
    state.getReadRuns().put(pid, u);
    state.getReadRuns().get(pid).getAnnotations().put("starttime", u.getArtifact().getAnnotationValue("time"));


    } else {
    //flush out all the write runs

    //get all processes
    Set<String> processes = state.getWriteRuns().keySet();

    //iterate over each key and send the write edge through
    Iterator<String> it = processes.iterator();
    while (it.hasNext()) {

    this.putEdgeThrough(state.getWriteRuns().get(it.next()));

    }

    //all the writes have been sent through. Now start a new read run
    state.getWriteRuns().clear();
    state.setCurrentRun(0);
    state.getReadRuns().put(pid, u);
    state.getReadRuns().get(pid).getAnnotations().put("starttime", u.getArtifact().getAnnotationValue("time"));


    }

    }

    } else {
    //this is a new file with no previous state so no need to check for previous runs just create state for it (done)
    ReadWriteRuns newState = new ReadWriteRuns();
    //and start a read run for the current process
    newState.getReadRuns().put(pid, u);
    newState.setCurrentRun(0);
    //put the file in the files table
    files.put(filename, newState);
    files.get(filename).getReadRuns().get(pid).getAnnotations().put("starttime", u.getArtifact().getAnnotationValue("time"));


    }
    return true;

    }

    @Override
    public boolean putEdge(WasControlledBy wcb) {
    if (chain == false) {
    storage.putEdge(wcb);
    } else {
    chainFilter.putEdge(wcb);
    }
    return true;

    }

    @Override
    public boolean putEdge(WasDerivedFrom wdf) {
    if (chain == false) {
    storage.putEdge(wdf);
    } else {
    chainFilter.putEdge(wdf);
    }
    return true;

    }

    @Override
    public boolean putEdge(WasGeneratedBy wgb) {
    //first of all get the filename
    String filename = wgb.getArtifact().getAnnotationValue("filename");
    //get the pid of the reading process
    String pid = wgb.getProcess().getAnnotationValue("pid");

    //check if you are keeping state for this file already or not otherwise create state for this file
    if (files.containsKey(filename)) {
    //we have state for this file. so this is not a new file
    //get that state
    ReadWriteRuns state = (ReadWriteRuns) files.get(filename);

    //check if this is a new write or a write run
    if (state.getCurrentRun() == 1) {
    //continuing write


    //now check if we have state for this process

    if (state.getWriteRuns().containsKey(pid)) {
    //this process already has a run with this file so update the write run for this process
    //update the write here!!! DO THIS HERE

    //update the version of the file. nothing else to do really
    state.getWriteRuns().get(pid).getAnnotations().put("endtime", wgb.getArtifact().getAnnotationValue("time"));

    } else {
    //process hasn't written the file yet so start a new run for it (done)
    files.get(filename).getWriteRuns().put(pid, wgb);
    state.getWriteRuns().get(pid).getAnnotations().put("starttime", wgb.getArtifact().getAnnotationValue("time"));

    }


    } else {
    //new write. could be end of a read run. or just start of a fresh write run. check for both (done)

    //see if there are reads to flush
    if (state.getCurrentRun() == -1) {
    //first ever run no need to flush previous read runs
    state.setCurrentRun(1);
    state.getWriteRuns().put(pid, wgb);
    state.getWriteRuns().get(pid).getAnnotations().put("starttime", wgb.getArtifact().getAnnotationValue("time"));


    } else {
    //flush out all the read runs

    //get all processes
    Set<String> processes = state.getReadRuns().keySet();

    //iterate over each key and send the write edge through
    Iterator<String> it = processes.iterator();
    while (it.hasNext()) {

    this.putEdgeThrough(state.getReadRuns().get(it.next()));

    }

    //all the reads have been sent through. Now start a new write run
    state.getReadRuns().clear();
    state.setCurrentRun(1);
    state.getWriteRuns().put(pid, wgb);
    state.getWriteRuns().get(pid).getAnnotations().put("starttime", wgb.getArtifact().getAnnotationValue("time"));


    }

    }

    } else {
    //this is a new file with no previous state so no need to check for previous runs just create state for it (done)
    ReadWriteRuns newState = new ReadWriteRuns();
    //and start a read run for the current process
    newState.getWriteRuns().put(pid, wgb);
    newState.setCurrentRun(1);
    //put the file in the files table
    files.put(filename, newState);
    files.get(filename).getWriteRuns().get(pid).getAnnotations().put("starttime", wgb.getArtifact().getAnnotationValue("time"));


    }

    return true;

    }

    @Override
    public boolean putEdge(WasTriggeredBy wtb) {
    if (chain == false) {
    storage.putEdge(wtb);
    } else {
    chainFilter.putEdge(wtb);
    }
    return true;

    }

    //private methods
    private boolean putEdgeThrough(WasGeneratedBy wgb) {

    if (chain == false) {
    storage.putEdge(wgb);
    } else {
    chainFilter.putEdge(wgb);
    }
    return true;

    }

    private boolean putEdgeThrough(Used u) {

    if (chain == false) {
    storage.putEdge(u);
    } else {
    chainFilter.putEdge(u);
    }
    return true;


    }

    public boolean next(Object o) {
    throw new UnsupportedOperationException("Not supported yet.");
    }
     */
}
