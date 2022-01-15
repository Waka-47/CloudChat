package application.lab_6;

import application.domain.*;
import application.exceptions.RepositoryException;
import application.service.SuperService;
import application.utils.WarningBox;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.io.IOException;

public class NetworkController {
    @FXML
    private TextField textFieldUser;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label signUpButton;

    SuperService superService;
    private Scene signUpScene;

    public NetworkController(SuperService superService) {
        this.superService = superService;
    }

    @FXML
    protected void tryLogin(ActionEvent event) {
        try {

            String username = textFieldUser.getText();
            String password = passwordField.getText();


            Integer id = superService.loginUser(username, password);
            if (id > 0) {
                User user = superService.findUser(id);
                // create user's main page
                FXMLLoader fxmlLoader = new FXMLLoader();
                MainPageController mainPageController = new MainPageController(user, superService, false);
                fxmlLoader.setLocation(getClass().getResource("mainpage.fxml"));
                fxmlLoader.setController(mainPageController);
                Scene mainScene = new Scene(fxmlLoader.load());
                Stage mainStage = new Stage();
                mainStage.setTitle("Cloud Chat");
                mainStage.setScene(mainScene);
                mainStage.show();
                if (signUpScene!=null)
                    signUpScene.getWindow().getScene().getWindow().hide();
                ((Node) (event.getSource())).getScene().getWindow().hide();
            } else
                WarningBox.show("Invalid login information!");

        } catch (RepositoryException | NumberFormatException | IOException e) {
            WarningBox.show(e.getMessage());
        }
    }

    @FXML
    protected void signUpView(MouseEvent event) {
        try {
            if (signUpScene!=null)
                signUpScene.getWindow().getScene().getWindow().hide();
            FXMLLoader fxmlLoader = new FXMLLoader();
            SignUpController signUpController = new SignUpController(superService);
            fxmlLoader.setLocation(getClass().getResource("signUp.fxml"));
            fxmlLoader.setController(signUpController);
            Scene mainScene = new Scene(fxmlLoader.load());
            Stage mainStage = new Stage();
            mainStage.setTitle("Sign up to the Network!");
            mainStage.setScene(mainScene);
            signUpScene=mainScene;
            mainStage.show();
        } catch (IOException e) {
            WarningBox.show(e.getMessage());
        }
    }

}
