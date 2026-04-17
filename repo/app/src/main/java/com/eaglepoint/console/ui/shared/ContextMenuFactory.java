package com.eaglepoint.console.ui.shared;

import com.eaglepoint.console.model.Bed;
import com.eaglepoint.console.model.PickupPoint;
import com.eaglepoint.console.model.Scorecard;
import com.eaglepoint.console.ui.bed.BedBoardController;
import com.eaglepoint.console.ui.evaluation.EvaluationController;
import com.eaglepoint.console.ui.pickup.PickupPointController;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

public class ContextMenuFactory {

    public static ContextMenu buildForBed(Bed bed, BedBoardController controller) {
        ContextMenu menu = new ContextMenu();
        MenuItem transition = new MenuItem("Change State...");
        transition.setOnAction(e -> controller.showTransitionDialog(bed));
        MenuItem auditItem = buildAuditTrailItem("BED", bed.getId());
        menu.getItems().addAll(transition, new SeparatorMenuItem(), auditItem);
        return menu;
    }

    public static ContextMenu buildForPickupPoint(PickupPoint pp, PickupPointController controller) {
        ContextMenu menu = new ContextMenu();
        MenuItem pause = new MenuItem("Pause Service...");
        pause.setOnAction(e -> controller.showPauseDialog(pp));
        pause.setDisable(!"ACTIVE".equals(pp.getStatus()));

        MenuItem resume = new MenuItem("Resume Service");
        resume.setOnAction(e -> controller.showResumeDialog(pp));
        resume.setDisable(!"PAUSED".equals(pp.getStatus()));

        MenuItem auditItem = buildAuditTrailItem("PICKUP_POINT", pp.getId());
        menu.getItems().addAll(pause, resume, new SeparatorMenuItem(), auditItem);
        return menu;
    }

    public static ContextMenu buildForScorecard(Scorecard scorecard, EvaluationController controller) {
        ContextMenu menu = new ContextMenu();
        MenuItem submit = new MenuItem("Submit");
        submit.setOnAction(e -> controller.submitScorecard(scorecard));
        submit.setDisable(!"DRAFT".equals(scorecard.getStatus()));

        MenuItem flagConflict = new MenuItem("Flag Conflict...");
        flagConflict.setOnAction(e -> controller.flagConflict(scorecard));

        MenuItem auditItem = buildAuditTrailItem("SCORECARD", scorecard.getId());
        menu.getItems().addAll(submit, flagConflict, new SeparatorMenuItem(), auditItem);
        return menu;
    }

    public static MenuItem buildAuditTrailItem(String entityType, long entityId) {
        MenuItem item = new MenuItem("View Audit Trail");
        item.setOnAction(e -> AuditTrailDialog.show(entityType, entityId));
        return item;
    }
}
