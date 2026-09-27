package com.study.travel_guide.mapper;

import com.study.travel_guide.entity.Expense;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExpenseMapper {

    @Insert("INSERT INTO expense(trip_id, category, amount, note, expense_date) " +
            "VALUES(#{tripId}, #{category}, #{amount}, #{note}, #{expenseDate})")
    int insert(@Param("tripId") Long tripId, @Param("category") String category,
               @Param("amount") BigDecimal amount, @Param("note") String note,
               @Param("expenseDate") LocalDate expenseDate);

    @Select("SELECT * FROM expense WHERE trip_id = #{tripId} ORDER BY expense_date DESC, id DESC")
    List<Expense> listByTrip(@Param("tripId") Long tripId);

    @Update("UPDATE expense SET category = #{category}, amount = #{amount}, note = #{note}, expense_date = #{expenseDate} " +
            "WHERE id = #{id} AND trip_id = #{tripId}")
    int update(@Param("id") Long id, @Param("tripId") Long tripId, @Param("category") String category,
               @Param("amount") BigDecimal amount, @Param("note") String note, @Param("expenseDate") LocalDate expenseDate);

    @Delete("DELETE FROM expense WHERE id = #{id} AND trip_id = #{tripId}")
    int delete(@Param("id") Long id, @Param("tripId") Long tripId);

    @Select("SELECT category, SUM(amount) AS total FROM expense WHERE trip_id = #{tripId} GROUP BY category")
    List<Map<String, Object>> sumByCategory(@Param("tripId") Long tripId);
}
