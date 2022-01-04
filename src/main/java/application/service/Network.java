package application.service;

import application.domain.Friendship;
import application.domain.Tuple;
import application.domain.User;
import application.domain.FriendDTO;
import application.domain.validator.Validator;
import application.exceptions.RepositoryException;
import application.exceptions.ValidationException;
import application.repository.Repository;
import application.utils.observer.Observable;
import application.utils.observer.Observer;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static application.utils.Constants.DATE_TIME_FORMATTER;


public class Network implements Observable {
    //TODO missing comments

    // ===================== NETWORK ==========================
    private final Repository<Integer, User> userRepository; // stores the users of the network
    private final Validator<User> userValidator; // validates the users
    private final Repository<Tuple<Integer, Integer>, Friendship> friendshipRepository; // stores the friendships of the network
    private final Validator<Friendship> friendshipValidator; // validates the friendships


    private List<Observer> observers=new ArrayList<>(); // list of observer objects that observe for changes in this service

    /**
     * Constructor
     * @param userRepository Repository(Integer,User)
     * @param userValidator Validator(User)
     * @param friendshipRepository Repository(Tuple(Integer,Integer),Friendship)
     * @param friendshipValidator Validator(Friendship)
     * @throws RepositoryException if the pre-loaded friendships are invalid
     */
    public Network(Repository<Integer, User> userRepository,
                   Validator<User> userValidator,
                   Repository<Tuple<Integer, Integer>, Friendship> friendshipRepository,
                   Validator<Friendship> friendshipValidator) throws RepositoryException, SQLException, ValidationException {

        this.userRepository = userRepository;
        this.userValidator = userValidator;
        this.friendshipRepository = friendshipRepository;
        this.friendshipValidator = friendshipValidator;
    }

    /**
     * Gets the friend list of the given user
     * @param user List(User)
     * @return the friend list of the user as Iterable
     * @throws SQLException if the database cannot be reached
     * @throws RepositoryException if the user doesn't exist
     * @throws ValidationException if the users or friendships are invalid
     */
    public List<User> friendList(User user) throws SQLException, RepositoryException, ValidationException {
        List<User> list = new ArrayList<>();

        for (Friendship f : friendshipRepository.getAll()){
            if(user.getId().equals(f.getId().getLeft())){
                list.add(userRepository.find(f.getId().getRight()));
            }
            else if(user.getId().equals(f.getId().getRight())){
                list.add(userRepository.find(f.getId().getLeft()));
            }
        }
        return list;
    }

    /**
     * Gets a string list of all the friendships the user has
     * @param userId Integer
     * @return List(String)
     * @throws ValidationException if data is not valid
     * @throws SQLException if the database isn't available
     * @throws RepositoryException if a user doesn't exist
     */
    public List<String> getFriendshipsOfUser(Integer userId) throws ValidationException, SQLException, RepositoryException {

        List<Friendship> userFriendship = friendshipRepository.getAll();
        userRepository.find(userId);
        return userFriendship
                .stream()
                .filter(x -> (x.getId().getLeft().equals(userId) || x.getId().getRight().equals(userId)))
                .map(x -> {
                        String friendString = "";
                        try {
                            User friendUser;
                            if (x.getId().getLeft().equals(userId)) {//the friend is the right one
                                friendUser = userRepository.find(x.getId().getRight());
                            }
                            else {//the friend is the left one
                                friendUser = userRepository.find(x.getId().getLeft());
                            }

                            friendString =
                                    friendUser.getFirstName() + " | " +
                                            friendUser.getLastName() + " | " +
                                            x.getDate().format(DATE_TIME_FORMATTER);

                        } catch (RepositoryException e) {
                            e.printStackTrace();
                        }
                    return friendString;

                })
                .collect(Collectors.toList());
    }

    /**
     * Gets a string list of all the friendships a user has made in a specific month
     * @param userId Integer
     * @param month Integer
     * @return List(String)
     * @throws ValidationException if the friendships are invalid
     * @throws SQLException if the database cannot be reached
     * @throws RepositoryException if the user doesn't exist
     */
    public List<String> getFriendshipsOfUserFromMonth(Integer userId, Integer month) throws ValidationException, SQLException, RepositoryException {
        if (month < 1 || month > 12) throw new ValidationException("Invalid month!");
        List<String> unfilteredList = getFriendshipsOfUser(userId);
        return unfilteredList
                .stream()
                .filter(x ->{
                    String dateString=x.split("\\|")[2].substring(1); // get date from string with name and date
                    LocalDateTime date = LocalDateTime.parse(dateString,DATE_TIME_FORMATTER);
                    return date.getMonthValue() == month; // verify if month is correct
                })
                .collect(Collectors.toList());
    }

    public List<FriendDTO> getFriendDtoOfUser(Integer userId) throws RepositoryException, SQLException, ValidationException {
        List<Friendship> userFriendship = friendshipRepository.getAll();
        userRepository.find(userId);
        return userFriendship
                .stream()
                .filter(x -> (x.getId().getLeft().equals(userId) || x.getId().getRight().equals(userId)))
                .map(friendship -> {
                    FriendDTO friend = new FriendDTO();
                    try {
                        User friendUser;
                        if (friendship.getId().getLeft().equals(userId)) {//the friend is the right one
                            friendUser = userRepository.find(friendship.getId().getRight());
                        }
                        else {//the friend is the left one
                            friendUser = userRepository.find(friendship.getId().getLeft());
                        }
                        friend.setId(friendUser.getId());
                        friend.setName(friendUser.getFirstName() + " " + friendUser.getLastName());
                        friend.setDate(friendship.getDate().format(DATE_TIME_FORMATTER));


                    } catch (RepositoryException e) {
                        e.printStackTrace();
                    }
                    return friend;

                })
                .collect(Collectors.toList());
    }

    //==================== USERS ==========================

    /**
     * Adds a user to the network
     * @param firstName String
     * @param lastName String
     * @throws ValidationException if the user is not valid
     * @throws IOException if the user cannot be parsed
     */
    public User addUser(String firstName, String lastName) throws ValidationException, IOException, RepositoryException {
        User user = new User(firstName, lastName);

        userValidator.validate(user);

        User added = userRepository.add(user);
        notifyObservers();
        return added;
    }

    /**
     * Deletes a user from the network and returns his value
     * @param id Integer
     * @return User
     * @throws SQLException if the database cannot be reached
     * @throws ValidationException if the user is invalid
     * @throws RepositoryException if the user doesn't exist
     */
    public User deleteUser(Integer id) throws IOException, RepositoryException, SQLException, ValidationException {

        User deleted = userRepository.delete(id);

        deleteFriendshipsOfUser(deleted);
        notifyObservers();
        return deleted;
    }

    /**
     * Update a user and returns the old value
     * @param id Integer
     * @param firstName String
     * @param lastName String
     * @return User
     * @throws ValidationException if the attributes are not valid
     * @throws RepositoryException if the user doesn't exist
     */
    public User updateUser(Integer id, String firstName, String lastName) throws ValidationException, IOException, RepositoryException, SQLException {
        User user = new User(firstName, lastName);
        user.setId(id);
        userValidator.validate(user);

        User updated = userRepository.update(user);
        notifyObservers();
        return updated;
    }

    /**
     * Gets all users of the network
     * @return List(User)
     * @throws SQLException if the database cannot be reached
     */
    public List<User> getAllUsers() throws SQLException, ValidationException, RepositoryException {
        return userRepository.getAll();
    }

    /**
     * Finds a user with the given id
     * @param id Integer
     * @return User
     * @throws RepositoryException if the user doesn't exist
     */
    public User findUser(Integer id) throws  RepositoryException {
        return userRepository.find(id);
    }

    //=================== FRIENDSHIPS =======================

    /**
     * Deletes friendships of a specific user
     * @param user User
     * @throws SQLException if the database cannot be reached
     * @throws RepositoryException if the user doesn't exist
     * @throws ValidationException if the friendship is invalid
     */
    private void deleteFriendshipsOfUser(User user) throws IOException, SQLException, RepositoryException, ValidationException {

        boolean done = false;
        while (!done) {
            for (Friendship f : friendshipRepository.getAll()) {
                if (user.getId().equals(f.getId().getLeft()) || user.getId().equals(f.getId().getRight())) {
                    friendshipRepository.delete(f.getId());
                    break;
                }
            }
            done = true;
        }
    }

    /**
     * Adds a friendship
     * @param leftId Integer
     * @param rightId Integer
     * @throws ValidationException if the friendship is invalid
     * @throws RepositoryException if the friendship already exists
     * @throws IOException if the values cannot be parsed
     */
    public void addFriendship(Integer leftId, Integer rightId) throws ValidationException, RepositoryException, IOException {

        if (leftId > rightId){
            Integer temp = leftId;
            leftId = rightId;
            rightId = temp;
        }

        Tuple<Integer, Integer> friendshipId = new Tuple<>(leftId, rightId);

        userRepository.find(leftId);
        userRepository.find(rightId);

        Friendship friendship = new Friendship(LocalDateTime.now());
        friendship.setId(friendshipId);

        friendshipValidator.validate(friendship);
        friendshipRepository.add(friendship);

        notifyObservers();
    }

    /**
     * Adds user references to friendship
     * @param f Friendship
     * @throws RepositoryException if the user doesn't exist
     */
    private void addUsersToFriendship(Friendship f) throws RepositoryException {
        User userLeft = userRepository.find(f.getId().getLeft());
        User userRight = userRepository.find(f.getId().getRight());
        f.setUserLeft(userLeft);
        f.setUserLeft(userRight);
    }

    /**
     * Adds user references to a list of friendships
     * @param friendships List(Friendship)
     * @throws RepositoryException if the users don't exist
     */
    private void addUsersToFriendshipList(List<Friendship> friendships) throws RepositoryException {
        for (Friendship f : friendships){
            addUsersToFriendship(f);
        }
    }

    /**
     * Deletes a friendship and returns its values
     * @param leftId Integer
     * @param rightId Integer
     * @return Friendship
     * @throws RepositoryException if the friendship doesn't exist
     */
    public Friendship deleteFriendship(Integer leftId, Integer rightId) throws IOException, RepositoryException, ValidationException, SQLException {
        if (leftId > rightId){
            Integer temp = leftId;
            leftId = rightId;
            rightId = temp;
        }
        Tuple<Integer, Integer> friendshipId = new Tuple<>(leftId, rightId);

        Friendship deleted = friendshipRepository.delete(friendshipId);
        notifyObservers();
        return deleted;
    }

    /**
     * Updates a friendship and returns the old value
     * @param leftId Integer
     * @param rightId Integer
     * @param date LocalDateTime
     * @return Friendship
     * @throws ValidationException if the friendship is invalid
     * @throws RepositoryException if the friendship doesn't exist
     */
    public Friendship updateFriendship(Integer leftId, Integer rightId, LocalDateTime date) throws ValidationException, IOException, RepositoryException, SQLException {

        if (leftId > rightId){
            Integer temp = leftId;
            leftId = rightId;
            rightId = temp;
        }
        Tuple<Integer, Integer> friendshipId = new Tuple<>(leftId, rightId);

        Friendship friendship = new Friendship(date);
        friendship.setId(friendshipId);

        friendshipValidator.validate(friendship);

        Friendship updatedFriendship = friendshipRepository.update(friendship);
        addUsersToFriendship(updatedFriendship);

        notifyObservers();
        return updatedFriendship;
    }


    /**
     * Finds a friendship
     * @param leftId Integer
     * @param rightId Integer
     * @return Friendship
     * @throws RepositoryException if the friendship doesn't exist
     */
    public Friendship findFriendship(Integer leftId, Integer rightId) throws RepositoryException {
        if (leftId > rightId){
            Integer temp = leftId;
            leftId = rightId;
            rightId = temp;
        }
        Tuple<Integer, Integer> friendshipId = new Tuple<>(leftId, rightId);
        Friendship foundFriendship = friendshipRepository.find(friendshipId);
        addUsersToFriendship(foundFriendship);

        return foundFriendship;
    }

    /**
     * Gets all friendships from the repository
     * @return  List(Friendship)
     * @throws ValidationException if one or more friendships are invalid
     * @throws RepositoryException if the users assigned to the friendships don't exist
     * @throws SQLException if the database cannot be reached
     */
    public List<Friendship> getAllFriendship() throws SQLException, ValidationException, RepositoryException {
        List<Friendship> friendships = friendshipRepository.getAll();
        addUsersToFriendshipList(friendships);
        return friendships;
    }




    /**
     * Returns a friendship matrix
     * @return int[][]
     * @throws SQLException if the database cannot be reached
     * @throws ValidationException
     * @throws RepositoryException
     */
    /*

    private int[][] getMatrix() throws SQLException, ValidationException, RepositoryException {

        friendshipRepository.getAll().forEach(f -> {
            matrix[f.getId().getLeft()][f.getId().getRight()] = 1;
            matrix[f.getId().getRight()][f.getId().getLeft()] = 1;
        });

        return matrix;
    }

     */

    /**
     * Returns the number of communities in the network
     * @return int
     * @throws SQLException if the database cannot be reached
     * @throws ValidationException
     * @throws RepositoryException
     */
    /*
    public int getCommunitiesNumber() throws SQLException, ValidationException, RepositoryException {
        int communitiesNumber = 0;
        int[][] matrix = getMatrix();
        List<User> list = userRepository.getAll();
        int size = nextId - 1;
        if (list.size() <= 0)
            return 0;
        int[] viz = new int[nextId+1];
        int[] c = new int[nextId+1];


        int start = list.get(0).getId();
        viz[start] = 1;
        int i = 1;
        int j = 1;

        boolean ok = true;
        while (ok){
            while (i <= j){
                for (int k = 1; k <= size; k++){
                    if(matrix[start][k] == 1 && viz[k] == 0){
                        j++;
                        c[j] = k;
                        viz[k] = 1;
                    }
                }
                i++;
                start = c[i];
            }
            int p = 1;
            while (viz[p] == 1 && p <= size){
                p++;
            }
            try {
                findUser(p);
                if (p > size) {
                    ok = false;
                } else {
                    start = p;
                    communitiesNumber++;
                    j++;
                    c[j] = p;
                    viz[p] = 1;
                }
            }
            catch(RepositoryException e) {ok = false;}
        }
        return communitiesNumber;
    }

     */


    /**
     * Adds observers to the current service
     * @param e Observer object
     */
    @Override
    public void addObserver(Observer e) {
        observers.add(e);
    }

    /**
     * Removes observers from the current service
     * @param e Observer object
     */
    @Override
    public void removeObserver(Observer e) {
        observers.remove(e);
    }

    /**
     * Notifies all observers of any changes occuring in the service
     */
    @Override
    public void notifyObservers() {
        observers.stream()
                .forEach(Observer::observerUpdate);
    }
}

