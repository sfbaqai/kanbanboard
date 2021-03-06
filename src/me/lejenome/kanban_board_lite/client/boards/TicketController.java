package me.lejenome.kanban_board_lite.client.boards;

import com.sun.javafx.scene.control.skin.ListViewSkin;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import me.lejenome.kanban_board_lite.client.App;
import me.lejenome.kanban_board_lite.client.NodeController;
import me.lejenome.kanban_board_lite.client.NotificationWatcher;
import me.lejenome.kanban_board_lite.client.RmiClient;
import me.lejenome.kanban_board_lite.common.Project;
import me.lejenome.kanban_board_lite.common.Ticket;
import me.lejenome.kanban_board_lite.common.TicketExistsException;
import me.lejenome.kanban_board_lite.common.TicketNotFoundException;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;

public class TicketController extends NodeController {

    @FXML
    Label name;
    @FXML
    HBox ticketsBox;
    @FXML
    ListView<Ticket> backlogTickets;
    @FXML
    ListView<Ticket> readyTickets;
    @FXML
    ListView<Ticket> inProgressTickets;
    @FXML
    ListView<Ticket> doneTickets;
    ListView<Ticket> focused = backlogTickets;
    ListView<Ticket> dragSource;
    HashMap<Integer, ListView<Ticket>> boards;
    HashMap<Integer, ObservableList<Ticket>> lists;
    private Vector<Ticket> tickets;
    private Project project;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        boards = new HashMap<>();
        lists = new HashMap<>();
        boards.put(0, backlogTickets);
        boards.put(3, readyTickets);
        boards.put(6, inProgressTickets);
        boards.put(9, doneTickets);

        for (Map.Entry<Integer, ListView<Ticket>> entry : boards.entrySet()) {
            ListView<Ticket> board = entry.getValue();
            board.setSkin(new ListViewSkinTicket(board));
            board.focusedProperty().addListener((e, a, b) -> {
                if (e.getValue()) focused = board;
            });
            board.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2)
                    edit(null);
            });
            board.setCellFactory(param -> {
                ListCellRenderer cell = new ListCellRenderer();
                cell.setOnDragDetected(e -> {
                    if (cell.getItem() != null) {
                        dragSource = cell.getListView();
                        Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                        ClipboardContent ctnt = new ClipboardContent();
                        ctnt.putString(String.valueOf(cell.getItem().getId()));
                        db.setContent(ctnt);
                    }
                    e.consume();
                });

                cell.setOnDragDone(e -> {
                    try {
                        lists.get(entry.getKey()).remove(cell.getIndex());
                        ((ListViewSkinTicket) board.getSkin()).refresh();
                    } catch (ArrayIndexOutOfBoundsException e1) {
                    }
                });

                return cell;
            });
            board.setOnDragOver(e -> {
                if (dragSource != board && e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            board.setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    try {
                        Ticket t = RmiClient.kanbanManager.getTicket(Integer.parseInt(db.getString()));
                        t.setStatus(entry.getKey());
                        RmiClient.kanbanManager.updateTicket(t);
                        lists.get(entry.getKey()).add(t);
                        ((ListViewSkinTicket) board.getSkin()).refresh();
                        success = true;
                    } catch (RemoteException | TicketNotFoundException | TicketExistsException e1) {
                        e1.printStackTrace();
                    }
                }
                e.setDropCompleted(success);
                e.consume();
            });
        }
        NotificationWatcher.addListener((target, id) -> {
            refresh(null);
        });

    }


    public void edit(ActionEvent actionEvent) {
        Stage stage = new Stage();
        TicketEditController ctrl = (TicketEditController) App.Load("boards/ticket.fxml", stage);
        ctrl.setTicket(focused.getSelectionModel().getSelectedItem());
        ctrl.setProject(project);
        ctrl.setProjectBoard(this);
        stage.show();
    }

    public void setProject(Project p) {
        this.project = p;
        name.setText(p.getName());
        refresh(null);
    }

    public void add(ActionEvent actionEvent) {
        Stage stage = new Stage();
        TicketEditController ctrl = (TicketEditController) App.Load("boards/ticket.fxml", stage);
        ctrl.setProject(project);
        ctrl.setProjectBoard(this);
        stage.show();
    }

    public void refresh(ActionEvent actionEvent) {
        try {
            this.tickets = RmiClient.kanbanManager.listTickets(project);

            for (Map.Entry<Integer, ListView<Ticket>> entry : boards.entrySet()) {
                ObservableList<Ticket> l = FXCollections.observableArrayList();
                SortedList<Ticket> list = new SortedList<Ticket>(l);
                lists.put(entry.getKey(), l);
                list.setComparator(new TicketCompator());
                entry.getValue().setItems(list);
            }

            for (Ticket t : tickets) {
                ObservableList<Ticket> b = lists.get(t.getStatus());
                if (b != null)
                    b.add(t);
                else
                    System.out.println("BOARD for STATUS " + t.getStatus() + " NOT FOUND!!!!");
            }
            for (ListView<Ticket> board : boards.values()) {
                ((ListViewSkinTicket) board.getSkin()).refresh();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    class ListCellRenderer extends ListCell<Ticket> {

        private final static int colorTo = 100;

        @Override
        protected void updateItem(Ticket ticket, boolean empty) {
            super.updateItem(ticket, empty);
            if (ticket != null) {
                TicketItem item = new TicketItem(ticket);
                setGraphic(item);
            }
            setStyle("-fx-padding: 0");
        }
    }

    public class TicketCompator implements Comparator<Ticket> {

        @Override
        public int compare(Ticket o1, Ticket o2) {
            if (o1 == null || o2 == null)
                return 0;
            if (o1.getPriority() != o2.getPriority())
                return o2.getPriority() - o1.getPriority();

            if (o1.getDue() != null && o2.getDue() != null) {
                int dueCompare = o1.getDue().compareTo(o2.getDue());
                if (dueCompare != 0)
                    return dueCompare;
            }
            return o1.getId() - o2.getId();
        }
    }

    public class ListViewSkinTicket extends ListViewSkin<Ticket> {
        public ListViewSkinTicket(ListView<Ticket> listView) {
            super(listView);
        }

        public void refresh() {
            super.flow.recreateCells();
        }
    }
}
