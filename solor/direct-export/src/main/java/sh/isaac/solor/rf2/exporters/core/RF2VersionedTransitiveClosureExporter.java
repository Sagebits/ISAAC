package sh.isaac.solor.rf2.exporters.core;

import sh.isaac.api.Get;
import sh.isaac.api.chronicle.LatestVersion;
import sh.isaac.api.logic.LogicNode;
import sh.isaac.api.logic.LogicalExpression;
import sh.isaac.api.logic.NodeSemantic;
import sh.isaac.api.observable.semantic.version.ObservableLogicGraphVersion;
import sh.isaac.model.logic.node.internal.ConceptNodeWithNids;
import sh.isaac.model.logic.node.internal.RoleNodeAllWithNids;
import sh.isaac.model.logic.node.internal.RoleNodeSomeWithNids;
import sh.isaac.solor.rf2.config.RF2Configuration;
import sh.isaac.solor.rf2.exporters.RF2DefaultExporter;
import sh.isaac.solor.rf2.utility.RF2ExportHelper;

import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

public class RF2VersionedTransitiveClosureExporter extends RF2DefaultExporter {

    private final RF2ExportHelper rf2ExportHelper;
    private final IntStream intStream;
    private final Semaphore readSemaphore;

    public RF2VersionedTransitiveClosureExporter(RF2Configuration rf2Configuration, RF2ExportHelper rf2ExportHelper, IntStream intStream, Semaphore readSemaphore) {
        super(rf2Configuration);
        this.rf2ExportHelper = rf2ExportHelper;
        this.intStream = intStream;
        this.readSemaphore = readSemaphore;
    }

    @Override
    protected Void call() throws Exception {

        try{

            this.intStream
                    .forEach(nid ->{
                        LogicalExpression logicalExpression = ((LatestVersion<ObservableLogicGraphVersion>)
                                this.rf2ExportHelper.getSnapshotService()
                                        .getObservableSemanticVersion(nid)).get().getLogicalExpression();

                        int stampNid = rf2ExportHelper.getSnapshotService()
                                .getObservableSemanticVersion(nid).getStamps().findFirst().getAsInt();

                        logicalExpression.processDepthFirst((logicNode, treeNodeVisitData) -> {

                            if (logicNode.getNodeSemantic() == NodeSemantic.CONCEPT) {

                                int conceptChronologyNid = logicNode.getNidForConceptBeingDefined();

                                LogicNode parentNode = null;
                                LogicNode tempNode = logicNode;

                                do {
                                    if (parentNode != null)
                                        tempNode = parentNode;

                                    int parentIndex = treeNodeVisitData.getPredecessorNid(tempNode.getNodeIndex()).getAsInt();
                                    parentNode = logicalExpression.getNode(parentIndex);

                                } while (!(parentNode.getNodeSemantic() == NodeSemantic.NECESSARY_SET ||
                                        parentNode.getNodeSemantic() == NodeSemantic.SUFFICIENT_SET ||
                                        parentNode.getNodeSemantic() == NodeSemantic.ROLE_ALL |
                                                parentNode.getNodeSemantic() == NodeSemantic.ROLE_SOME));

                                if (parentNode.getNodeSemantic() == NodeSemantic.NECESSARY_SET || parentNode.getNodeSemantic() == NodeSemantic.SUFFICIENT_SET) {
                                    final StringBuilder sb1 = new StringBuilder();

                                    super.writeToFile(
                                            sb1.append(rf2ExportHelper.getIdString(conceptChronologyNid) + "\t")
                                                    .append(rf2ExportHelper.getIdString(((ConceptNodeWithNids) logicNode).getConceptNid()) + "\t")
                                                    .append(rf2ExportHelper.getTimeString(stampNid) + "\t")
                                                    .append(rf2ExportHelper.getActiveString(stampNid) + "\t")
                                                    .append("\r")
                                                    .toString()
                                    );

                                } else if (parentNode instanceof RoleNodeAllWithNids) {

                                    final StringBuilder sb2 = new StringBuilder();

                                    super.writeToFile(
                                            sb2.append(rf2ExportHelper.getIdString(conceptChronologyNid) + "\t")
                                                    .append(rf2ExportHelper.getIdString(((ConceptNodeWithNids) logicNode).getConceptNid()) + "\t")
                                                    .append(rf2ExportHelper.getTimeString(stampNid) + "\t")
                                                    .append(rf2ExportHelper.getActiveString(stampNid) + "\t")
                                                    .append("\r")
                                                    .toString()
                                    );


                                } else if (parentNode instanceof RoleNodeSomeWithNids) {

                                    final StringBuilder sb3 = new StringBuilder();

                                    super.writeToFile(
                                            sb3.append(rf2ExportHelper.getIdString(conceptChronologyNid) + "\t")
                                                    .append(rf2ExportHelper.getIdString(((ConceptNodeWithNids) logicNode).getConceptNid()) + "\t")
                                                    .append(rf2ExportHelper.getTimeString(stampNid) + "\t")
                                                    .append(rf2ExportHelper.getActiveString(stampNid) + "\t")
                                                    .append("\r")
                                                    .toString()
                                    );
                                }
                            }
                        });
                    });

        }finally {
            this.readSemaphore.release();
            Get.activeTasks().remove(this);
        }

        return null;
    }
}