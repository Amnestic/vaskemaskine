package db;

import api.BookingDTO;
import api.UsageAdminExportDTO;
import api.UsageDTO;
import db.mappers.BookingMapper;
import db.mappers.UsageAdminExportMapper;
import db.mappers.UsageMapper;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import java.util.Date;
import java.util.List;

@RegisterMapper(BookingMapper.class)
public interface BookingDAO {

    @SqlUpdate("CREATE TABLE IF NOT EXISTS bookings (" +
            "id SERIAL," +
            "start_time TIMESTAMP NOT NULL," +
            "end_time TIMESTAMP NOT NULL," +
            "owner VARCHAR(100) NOT NULL references users(username)," +
            "number_of_washing_machine_uses SMALLINT NOT NULL," +
            "number_of_tumble_dry_uses SMALLINT NOT NULL," +
            "PRIMARY KEY(id)" +
            ");")
    void createBookingTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS bookings_rev (" +
            "booking_id INTEGER," +
            "start_time TIMESTAMP NOT NULL," +
            "end_time TIMESTAMP NOT NULL," +
            "owner VARCHAR(100) NOT NULL references users(username)," +
            "number_of_washing_machine_uses SMALLINT NOT NULL," +
            "number_of_tumble_dry_uses SMALLINT NOT NULL," +
            "update_type VARCHAR(10)" +
            ");")
    void createBookingRevTable();

    @SqlUpdate("CREATE TRIGGER booking_rev_trigger" +
            "AFTER INSERT OR UPDATE OR DELETE ON bookings")
    void createBookingRevTrigger();

    @SqlUpdate("INSERT INTO bookings (start_time, end_time, owner, number_of_washing_machine_uses, number_of_tumble_dry_uses) " +
            "VALUES (:bookingDTO.startTime, :bookingDTO.endTime, :bookingDTO.owner, :bookingDTO.numberOfWashingMachineUses, :bookingDTO.numberOfTumbleDryUses)")
    void insertBooking(@BindBean("bookingDTO") BookingDTO bookingDTO);

    /**
     *
     * @param startTime starting time of when to retrieve bookings from
     * @param endTime ending time of when to retrieve bookings from
     * @param username the username provided will provide detailed information about the booking,
     *                 for other bookings only the start and end time and the owner of it will be provided.
     * @return bookings in interval, detailed bookings for the username provided
     */
    @SqlQuery("SELECT bookings_table.id, start_time, end_time, users.name, users.username as owner, users.apartment, number_of_washing_machine_uses, number_of_tumble_dry_uses " +
            "FROM users " +
            "JOIN " +
            "(SELECT id, start_time, end_time, owner, " +
            "CASE number_of_washing_machine_uses WHEN 0 THEN 0 ELSE 0 END AS number_of_washing_machine_uses, " +
            "CASE number_of_tumble_dry_uses WHEN 0 THEN 0 ELSE 0 END AS number_of_tumble_dry_uses " +
            "FROM bookings " +
            "WHERE start_time >= :startTime " +
            "AND :endTime >= end_time " +
            "AND owner != :username " +
            "UNION " +
            "SELECT * FROM bookings WHERE start_time >= :startTime AND :endTime >= end_time AND owner = :username) bookings_table " +
            "ON bookings_table.owner = users.username")
    List<BookingDTO> getBookingsInInterval(@Bind("startTime") Date startTime, @Bind("endTime") Date endTime, @Bind("username") String username);

    @SqlQuery("SELECT bookings.id, start_time, end_time, owner, name, apartment, number_of_washing_machine_uses, number_of_tumble_dry_uses " +
            "FROM bookings JOIN users ON bookings.owner = users.username " +
            "WHERE start_time < :endTime AND end_time > :startTime")
    List<BookingDTO> getBookingsOverlappingInterval(@Bind("startTime") Date startTime, @Bind("endTime") Date endTime);

    @SqlUpdate("DELETE FROM bookings WHERE id = :id AND owner = :username")
    int deleteBooking(@Bind("username") String username, @Bind("id") int id);

    @SqlQuery("SELECT bookings.id, start_time, end_time, owner, name, apartment, number_of_washing_machine_uses, number_of_tumble_dry_uses " +
            "FROM bookings JOIN users ON bookings.owner = users.username " +
            "WHERE start_time = :startTime AND end_time = :endTime AND owner = :owner")
    BookingDTO getBookingFromOwnerAndDates(@Bind("owner") String owner, @Bind("startTime") Date startTime,
                                           @Bind("endTime") Date endTime);

    @SqlQuery("SELECT bookings.id, start_time, end_time, owner, name, apartment, number_of_washing_machine_uses, number_of_tumble_dry_uses " +
            "FROM bookings JOIN users ON bookings.owner = users.username " +
            "WHERE bookings.id = :id AND owner = :username")
    BookingDTO getBookingFromId(@Bind("username") String username, @Bind("id") int id);

    @SqlUpdate("UPDATE bookings " +
            "SET start_time = :startTime, end_time = :endTime," +
            "number_of_washing_machine_uses = :numberOfWashingMachineUses," +
            "number_of_tumble_dry_uses = :numberOfTumbleDryUses " +
            "WHERE id = :id AND owner = :username")
    int updateBooking(@Bind("username") String username, @Bind("id") int id,
                      @Bind("startTime") Date startTime, @Bind("endTime") Date endTime,
                      @Bind("numberOfWashingMachineUses") int numberOfWashingMachineUses,
                      @Bind("numberOfTumbleDryUses") int numberOfTumbleDryUses);

    @RegisterMapper(UsageMapper.class)
    @SqlQuery("SELECT " +
            "to_char(start_time, 'Mon') as mon, "+
            "extract(year from start_time) as year, "+
            "SUM(number_of_washing_machine_uses) sum_of_washing_machine_uses, "+
            "SUM(number_of_tumble_dry_uses) sum_of_tumble_dry_uses "+
            "FROM bookings "+
            "WHERE owner = :username "+
            "AND start_time >= :startTime "+
            "AND end_time <= :endTime "+
            "GROUP BY (1, 2)")
    List<UsageDTO> getUsageInInterval(@Bind("username") String username, @Bind("startTime") Date startTime, @Bind("endTime") Date endTime);

    /**
     *
     * @param startTime The starting point of where to retrieve usage for
     * @param endTime The end point of where to retrieve usage for
     * @return usage for every user in the interval together with their real name and apartment
     */
    @RegisterMapper(UsageAdminExportMapper.class)
    @SqlQuery("SELECT name, apartment, mon, year, sum_of_washing_machine_uses, sum_of_tumble_dry_uses " +
            "FROM users " +
            "JOIN " +
            "(SELECT " +
            "to_char(start_time, 'Mon') as mon, " +
            "extract(year from start_time) as year, " +
            "owner as username, " +
            "SUM(number_of_washing_machine_uses) sum_of_washing_machine_uses, " +
            "SUM(number_of_tumble_dry_uses) sum_of_tumble_dry_uses " +
            "FROM bookings " +
            "WHERE start_time >= :startTime " +
            "AND end_time <= :endTime " +
            "GROUP BY (1, 2, 3)) usage " +
            "ON usage.username = users.username")
    List<UsageAdminExportDTO> getUsageInIntervalAdmin(@Bind("startTime") Date startTime, @Bind("endTime") Date endTime);

    @SqlUpdate("TRUNCATE TABLE bookings")
    void truncateTable();
}
