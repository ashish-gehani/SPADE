/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2010 SRI International

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class ReadWriteFilter implements Filter {

    boolean chain;
    Filter chainFilter;
    StorageInterface storage;
    private HashMap<String, ReadWriteRuns> files;
    //files is basically hashmap from file path -> read/write runs for all processes

    public ReadWriteFilter(StorageInterface inputStorage) {

        chain = false;
        files = new HashMap<String, ReadWriteRuns>();
        storage = inputStorage;
    }

    public ReadWriteFilter(StorageInterface inputStorage, Filter f) {

        chain = true;
        chainFilter = f;
        files = new HashMap<String, ReadWriteRuns>();
        storage = inputStorage;
    }

    @Override
    public void putVertex(Agent a) {
        // TODO Auto-generated method stub
        if (chain == false) {
            storage.putVertex(a);
        } else {
            chainFilter.putVertex(a);
        }

    }

    @Override
    public void putVertex(Process p) {
        // TODO Auto-generated method stub
        if (chain == false) {
            storage.putVertex(p);
        } else {
            chainFilter.putVertex(p);
        }

    }

    @Override
    public void putVertex(Artifact a) {
        // TODO Auto-generated method stub
        if (chain == false) {
            storage.putVertex(a);
        } else {
            chainFilter.putVertex(a);
        }

    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, Used u) {
        // TODO Auto-generated method stub

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

                        this.putEdgeThrough(v1, v2, state.getWriteRuns().get(it.next()));

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

    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasControlledBy wcb) {
        // TODO Auto-generated method stub


        if (chain == false) {
            storage.putEdge(wcb.getProcess(), wcb.getAgent(), wcb);
        } else {
            chainFilter.putEdge(v1, v2, wcb);
        }

    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasDerivedFrom wdf) {
        // TODO Auto-generated method stub
        if (chain == false) {
            storage.putEdge(wdf.getArtifact2(), wdf.getArtifact1(), wdf);
        } else {
            chainFilter.putEdge(v1, v2, wdf);
        }

    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasGeneratedBy wgb) {
        // TODO Auto-generated method stub

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

                        this.putEdgeThrough(v1, v2, state.getReadRuns().get(it.next()));

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


    }

    @Override
    public void putEdge(Vertex v1, Vertex v2, WasTriggeredBy wtb) {
        // TODO Auto-generated method stub
        if (chain == false) {
            storage.putEdge(wtb.getProcess1(), wtb.getProcess2(), wtb);
        } else {
            chainFilter.putEdge(v1, v2, wtb);
        }

    }

    //private methods
    private void putEdgeThrough(Vertex v1, Vertex v2, WasGeneratedBy wgb) {
        // TODO Auto-generated method stub

        if (chain == false) {
            storage.putEdge(wgb.getArtifact(), wgb.getProcess(), wgb);
        } else {
            chainFilter.putEdge(v1, v2, wgb);
        }

    }

    private void putEdgeThrough(Vertex v1, Vertex v2, Used u) {
        // TODO Auto-generated method stub



        if (chain == false) {
            storage.putEdge(u.getProcess(), u.getArtifact(), u);
        } else {
            chainFilter.putEdge(v1, v2, u);
        }


    }
}
