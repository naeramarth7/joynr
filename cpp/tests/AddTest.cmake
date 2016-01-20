function(AddTest TARGET)
    add_executable(
        ${TARGET}
        ${ARGN}
    )
    set_target_properties(
        ${TARGET}
        PROPERTIES
        COMPILE_FLAGS "-Wno-effc++ -Wno-unused-parameter"
        AUTOMOC TRUE
    )
    add_dependencies(${TARGET} googletest)
    add_dependencies(${TARGET} googlemock)
endfunction(AddTest)
