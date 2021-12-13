package application.repository.database;

import application.domain.Message;
import application.domain.User;
import application.exceptions.RepositoryException;
import application.exceptions.ValidationException;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageDataBaseRepository extends DataBaseRepository<Integer, Message> {
    //TODO comments
    /**
     * constructor
     * @param url of database
     * @param username of the database account
     * @param password of the database account
     */
    public MessageDataBaseRepository(String url, String username, String password) {
        super(url, username, password);
    }

    private User findUser(Integer id) throws RepositoryException {

        String sql = "SELECT * from users where id = ?";
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)
        ){

            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();

            resultSet.next();
            Integer id1 = resultSet.getInt("id");
            String firstName = resultSet.getString("first_name");
            String lastName = resultSet.getString("last_name");
            User user = new User(firstName, lastName);
            user.setId(id1);

            resultSet.close();

            return user;
        }
        catch (SQLException throwable) {
            throw new RepositoryException("Nonexistent user!\n");
        }
    }
    @Override
    public Message find(Integer id) throws RepositoryException {

        try(Connection connection = DriverManager.getConnection(url, username, password);
            PreparedStatement messageStatement = connection.prepareStatement(
                    "SELECT * from message where id = ? ");
            PreparedStatement sendToStatement = connection.prepareStatement(
                    "SELECT user_id from send_to where message_id = ? ")) {


            //get fields from message table
            messageStatement.setInt(1, id);
            ResultSet messageResultSet = messageStatement.executeQuery();

            messageResultSet.next();

            Integer fromId = messageResultSet.getInt("from");
            User from = findUser(fromId);
            LocalDateTime date = LocalDateTime.parse(messageResultSet.getString("date"));
            String text = messageResultSet.getString("text");

            //get fields from send_to table

            sendToStatement.setInt(1, id);
            ResultSet sendToResultSet = sendToStatement.executeQuery();

            List<User> to = new ArrayList<>();
            while (sendToResultSet.next()){

                Integer userId = sendToResultSet.getInt(1);
                User toUser = findUser(userId);
                to.add(toUser);
            }
            sendToResultSet.close();
            //create Message object
            Message message = new Message(from, to, text, date);
            message.setId(id);
            //set replyOf field if necessary
            int replyOfId = messageResultSet.getInt("reply_of");
            if(replyOfId != 0)
                message.setReplyOf(find(replyOfId));

            return message;
        }
        catch (SQLException throwable) {
            throw new RepositoryException("Nonexistent message!\n");
        }
    }

    @Override
    public List<Message> getAll() throws RepositoryException {

        try(Connection connection = DriverManager.getConnection(url, username, password);
            PreparedStatement messageStatement = connection.prepareStatement(
                    "SELECT * from message")
        ) {


            ResultSet messageResultSet = messageStatement.executeQuery();
            List<Message> messageList = new ArrayList<>();

            while (messageResultSet.next()){
                //get fields from message table

                int id = messageResultSet.getInt("id");
                Integer fromId = messageResultSet.getInt("from");
                User from = findUser(fromId);
                LocalDateTime date = LocalDateTime.parse(messageResultSet.getString("date"));
                String text = messageResultSet.getString("text");

                //get fields from send_to table

                PreparedStatement sendToStatement = connection.prepareStatement(
                        "SELECT user_id from send_to where message_id = ? ");
                sendToStatement.setInt(1, id);
                ResultSet sendToResultSet = sendToStatement.executeQuery();

                List<User> to = new ArrayList<>();
                while (sendToResultSet.next()){

                    Integer userId = sendToResultSet.getInt(1);
                    User toUser = findUser(userId);
                    to.add(toUser);
                }
                sendToResultSet.close();

                Message message = new Message(from, to, text, date);
                message.setId(id);

                int replyOfId = messageResultSet.getInt("reply_of");
                if(replyOfId != 0)
                    message.setReplyOf(find(replyOfId));

                messageList.add(message);
            }

            return messageList;
        }
        catch (SQLException throwable) {
            throw new RepositoryException("Error database extraction!\n");
        }
    }

    @Override
    public Message add(Message message) throws IOException, RepositoryException {

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement messageStatement = connection.prepareStatement(
                     "insert into message (id, \"from\", date, text, reply_of) values (?, ?, ?, ?, ?)")
        ) {

            //insert into message table
            messageStatement.setInt(1, message.getId());
            messageStatement.setInt(2, message.getFrom().getId());
            messageStatement.setString(3, message.getDate().toString());
            messageStatement.setString(4, message.getText());
            if (message.getReplyOf().isPresent())
                messageStatement.setInt(5, message.getReplyOf().get().getId());
            else
                messageStatement.setNull(5, Types.INTEGER);

            messageStatement.executeUpdate();

            //insert into send_to table
            for (User user : message.getTo()){
                PreparedStatement sendToStatement = connection.prepareStatement(
                        "insert into send_to (message_id, user_id) values (?, ?)");
                sendToStatement.setInt(1, message.getId());
                sendToStatement.setInt(2, user.getId());
                sendToStatement.executeUpdate();
                sendToStatement.close();
            }

            return message;
        } catch (SQLException e) {
            throw new RepositoryException("The message already exists!\n");
        }
    }

    @Override
    public Message delete(Integer id) throws IOException, RepositoryException, SQLException, ValidationException {

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement messageStatement = connection.prepareStatement(
                     "delete from message where id = ?");
             PreparedStatement sendToStatement = connection.prepareStatement(
                     "delete from send_to where message_id = ?");
             PreparedStatement updateReplyToStatement = connection.prepareStatement(
                     "update message set reply_of = NULL where reply_of = ?")
        ) {

            Message deleted = find(id);

            //update reply_to column in message table
            updateReplyToStatement.setInt(1, id);
            updateReplyToStatement.executeUpdate();

            //delete from send_to table
            sendToStatement.setInt(1, id);
            sendToStatement.executeUpdate();

            //delete from message table
            messageStatement.setInt(1, id);
            messageStatement.executeUpdate();

            return deleted;

        } catch (SQLException throwable) {
            throw new RepositoryException("Nonexistent message!\n");
        }
    }

    @Override
    public Message update(Message message) throws IOException, RepositoryException, SQLException, ValidationException {

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement updateMessageStatement = connection.prepareStatement(
                     "update message set text = ? where id = ?");
             PreparedStatement deleteSendToStatement = connection.prepareStatement(
                     "delete from send_to where message_id = ?")
        ) {

            Message updated = find(message.getId());

            //update text
            updateMessageStatement.setString(1, message.getText());
            updateMessageStatement.setInt(2, message.getId());
            updateMessageStatement.executeUpdate();

            //delete from send_to table
            deleteSendToStatement.setInt(1, message.getId());
            deleteSendToStatement.executeUpdate();

            //add to send_to table
            for (User user : message.getTo()){
                PreparedStatement insertSendToStatement = connection.prepareStatement(
                        "insert into send_to (message_id, user_id) values (?, ?)");
                insertSendToStatement.setInt(1, message.getId());
                insertSendToStatement.setInt(2, user.getId());
                insertSendToStatement.executeUpdate();
                insertSendToStatement.close();
            }

            return updated;

        } catch (SQLException throwable) {
            throw new RepositoryException("Nonexistent message!\n");
        }
    }

    @Override
    public Integer size() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) as count from message");
             ResultSet resultSet = statement.executeQuery()) {

            resultSet.next();
            return resultSet.getInt("count");
        }
    }
}
