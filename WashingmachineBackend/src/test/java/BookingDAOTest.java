import api.BookingDTO;
import api.Usage;
import core.RoleHelper;
import db.BookingDAO;
import db.UserDAO;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class BookingDAOTest {
    private BookingDAO bookingDAO;
    private UserDAO userDAO;
    private final String USERNAME_1 = "user";
    private final String USERNAME_2 = "user2";

    @Before
    public void setup() {
        DBI dbi = new DBI("jdbc:postgresql://localhost:5433/test", "postgres", "root");
        bookingDAO = dbi.onDemand(BookingDAO.class);
        userDAO = dbi.onDemand(UserDAO.class);
        userDAO.createRoleTable();
        userDAO.createUsersTable();
        bookingDAO.createBookingTable();
        userDAO.insertUser(USERNAME_1, "password_that_should_have_been_hashed_and_salted", "bogus", RoleHelper.ROLE_DEFAULT);
        userDAO.insertUser(USERNAME_2, "password_that_should_have_been_hashed_and_salted", "bogus", RoleHelper.ROLE_DEFAULT);
    }

    @After
    public void tearDown() throws InterruptedException {
        bookingDAO.truncateTable();
        userDAO.truncateUsersTable();
    }

    @Test
    public void shouldBeAbleToFindBookingAfterItsInsertion() {
        Date startTime = new Date();
        Date endTime = new Date();
        BookingDTO bookingDTO = new BookingDTO(-1, startTime, endTime, USERNAME_1, 1, 1);
        bookingDAO.insertBooking(bookingDTO);
        bookingDTO = bookingDAO.getBookingFromOwnerAndDates(USERNAME_1, startTime, endTime);
        assertEquals(USERNAME_1, bookingDTO.getOwner());
    }

    @Test
    public void shouldBeAbleToFindBookingsInInterval() {
        // Create two bookings with different start end points
        Date startDate1, endDate1, startDate2, endDate2, startDate3, endDate3, searchDateStart, searchDateEnd;
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        startDate1 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 11);
        endDate1 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        startDate2 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 13);
        endDate2 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 15);
        startDate3 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 16);
        endDate3 = calendar.getTime();

        bookingDAO.insertBooking(new BookingDTO(-1, startDate1, endDate1, USERNAME_1, 0, 1));
        bookingDAO.insertBooking(new BookingDTO(-1, startDate2, endDate2, USERNAME_2, 0, 1));
        bookingDAO.insertBooking(new BookingDTO(-1, startDate3, endDate3, USERNAME_1, 123, 321));

        calendar.set(Calendar.HOUR_OF_DAY, 9);
        searchDateStart = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 14);
        searchDateEnd = calendar.getTime();

        List<BookingDTO> bookings = bookingDAO.getBookingsInInterval(searchDateStart, searchDateEnd, "");
        assertEquals(2, bookings.size());

        for (BookingDTO booking : bookings) {
            assertThat(booking.getStartTime(), Matchers.either(Matchers.is(startDate1)).or(Matchers.is(startDate2)));
            assertThat(booking.getEndTime(), Matchers.either(Matchers.is(endDate1)).or(Matchers.is(endDate2)));
        }
    }

    @Test
    public void shouldBeAbleToUpdateBooking() {
        Date startTime = new Date();
        Date endTime = new Date();
        bookingDAO.insertBooking(new BookingDTO(-1, startTime, endTime, USERNAME_1, 1, 0));
        BookingDTO bookingDTO = bookingDAO.getBookingFromOwnerAndDates(USERNAME_1, startTime, endTime);

        Date newStartDate = new Date(0);
        Date newEndDate = new Date();
        int bookingID = bookingDTO.getId();
        bookingDAO.updateBooking(USERNAME_1, bookingID, newStartDate, newEndDate, 321, 123);
        // Implicitly tests that that dates are updated
        bookingDTO = bookingDAO.getBookingFromOwnerAndDates(USERNAME_1, newStartDate, newEndDate);

        assertEquals("Should not create new row", bookingID, bookingDTO.getId());
        assertEquals(USERNAME_1, bookingDTO.getOwner());
        assertEquals(321, bookingDTO.getNumberOfWashingMachineUses());
        assertEquals(123, bookingDTO.getNumberOfTumbleDryUses());
    }

    @Test
    public void shouldOnlyBeAbleToUpdateAndDeleteOwnBookings() {
        Date startTime = new Date();
        Date endTime = new Date();
        BookingDTO bookingDTO = new BookingDTO(-1, startTime, endTime, USERNAME_2, 1, 1);
        bookingDAO.insertBooking(bookingDTO);
        BookingDTO insertedBooking = bookingDAO.getBookingFromOwnerAndDates(USERNAME_2, startTime, endTime);
        int numberOfAffectedRows = bookingDAO.updateBooking(USERNAME_1, insertedBooking.getId(), new Date(), new Date(), 1, 2);
        assertEquals(0, numberOfAffectedRows);

        numberOfAffectedRows = bookingDAO.deleteBooking(USERNAME_1, insertedBooking.getId());
        assertEquals(0, numberOfAffectedRows);
    }

    @Test
    public void shouldBeAbleToDeleteOwnBookings() {
        Date startTime = new Date();
        Date endTime = new Date();
        BookingDTO bookingDTO = new BookingDTO(-1, startTime, endTime, USERNAME_1, 1, 1);
        bookingDAO.insertBooking(bookingDTO);
        BookingDTO insertedBooking = bookingDAO.getBookingFromOwnerAndDates(USERNAME_1, startTime, endTime);
        int numberOfAffectedRows = bookingDAO.deleteBooking(USERNAME_1, insertedBooking.getId());
        assertEquals(1, numberOfAffectedRows);
    }

    @Test
    public void getBookingsOverlappingIntervalShouldWork() {
        // Create two bookings with different start end points
        Date startDate1, endDate1, startDate2, endDate2, startDate3, endDate3, searchDateStart, searchDateEnd;
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        startDate1 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 11);
        endDate1 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        startDate2 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 13);
        endDate2 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 15);
        startDate3 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 18);
        endDate3 = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 10);
        searchDateStart = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        searchDateEnd = calendar.getTime();

        bookingDAO.insertBooking(new BookingDTO(-1, startDate1, endDate1, USERNAME_1, 1, 2));
        bookingDAO.insertBooking(new BookingDTO(-1, startDate2, endDate2, USERNAME_2, 2, 2));
        bookingDAO.insertBooking(new BookingDTO(-1, startDate3, endDate3, USERNAME_1, 3, 2));

        // Could probably look at values of the returned values here, but meh
        List<BookingDTO> bookings = bookingDAO.getBookingsOverlappingInterval(searchDateStart, searchDateEnd);
        assertEquals(2, bookings.size());
    }

    @Test
    public void getBookingFromIdShouldWork() {
        Date startTime = new Date();
        Date endTime = new Date();
        BookingDTO bookingDTO = new BookingDTO(-1, startTime, endTime, USERNAME_1, 1, 1);
        bookingDAO.insertBooking(bookingDTO);
        bookingDTO = bookingDAO.getBookingFromOwnerAndDates(USERNAME_1, startTime, endTime);

        BookingDTO bookingDTOToTest = bookingDAO.getBookingFromId(USERNAME_2, bookingDTO.getId());
        assertEquals(null, bookingDTOToTest);
        bookingDTOToTest = bookingDAO.getBookingFromId(USERNAME_1, bookingDTO.getId());
        assertEquals(bookingDTOToTest.getOwner(), USERNAME_1);
    }

    @Test
    public void getBookingsInIntervalShouldOnlyReturnSensitiveDataOfOwnReservations() {
        Calendar calendar = Calendar.getInstance();
        BookingDTO ownBooking = new BookingDTO(123, calendar.getTime(), calendar.getTime(), USERNAME_1, 123, 321);
        BookingDTO someoneElsesBooking = new BookingDTO(123, calendar.getTime(), calendar.getTime(), USERNAME_2, 1337, 7331);

        bookingDAO.insertBooking(ownBooking);
        bookingDAO.insertBooking(someoneElsesBooking);

        calendar.add(Calendar.HOUR_OF_DAY, -1);
        Date startTimeForSearch = calendar.getTime();
        calendar.add(Calendar.HOUR_OF_DAY, 2);
        Date endTimeForSearch = calendar.getTime();

        List<BookingDTO> bookings = bookingDAO.getBookingsInInterval(startTimeForSearch, endTimeForSearch, USERNAME_1);

        // Get own bookings and the someone elses booking out
        BookingDTO ownBookingRetrieved = null;
        BookingDTO someoneElsesBookingRetrieved = null;
        for (BookingDTO booking : bookings) {
            if (booking.getOwner().equals(USERNAME_1)) {
                ownBookingRetrieved = booking;
            } else {
                someoneElsesBookingRetrieved = booking;
            }
        }

        assertEquals(USERNAME_1, ownBookingRetrieved.getOwner());
        assertEquals(123, ownBookingRetrieved.getNumberOfWashingMachineUses());
        assertEquals(321, ownBookingRetrieved.getNumberOfTumbleDryUses());

        assertEquals(USERNAME_2, someoneElsesBookingRetrieved.getOwner());
        assertEquals(0, someoneElsesBookingRetrieved.getNumberOfWashingMachineUses());
        assertEquals(0, someoneElsesBookingRetrieved.getNumberOfTumbleDryUses());
    }

    @Test
    public void getUsageInIntervalShouldWork() {
        // Create four bookings, two of owns in different times, 1 outside of interval of own, 1 not owned by self
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 30);
        BookingDTO firstBooking = new BookingDTO(123, calendar.getTime(), calendar.getTime(), USERNAME_1, 10, 10);
        calendar.set(Calendar.HOUR_OF_DAY, 11);
        BookingDTO secondBooking = new BookingDTO(123, calendar.getTime(), calendar.getTime(), USERNAME_1, 8, 15);
        BookingDTO thirdBooking = new BookingDTO(123, calendar.getTime(), calendar.getTime(), USERNAME_2, 123, 321);
        calendar.set(Calendar.HOUR_OF_DAY, 15);
        BookingDTO fourthBooking = new BookingDTO(123, calendar.getTime(), calendar.getTime(), USERNAME_1, 30, 30);

        // Insert bookings
        bookingDAO.insertBooking(firstBooking);
        bookingDAO.insertBooking(secondBooking);
        bookingDAO.insertBooking(thirdBooking);
        bookingDAO.insertBooking(fourthBooking);

        // Setup search dates
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        Date startSearchDate = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        Date endSearchDate = calendar.getTime();

        Usage usage = bookingDAO.getUsageInInterval(USERNAME_1, startSearchDate, endSearchDate);

        assertEquals(18, usage.getSumOfWashingMachineUses());
        assertEquals(25, usage.getSumOfTumbleDryUses());
        assertEquals(USERNAME_1, usage.getOwner());
    }
}
