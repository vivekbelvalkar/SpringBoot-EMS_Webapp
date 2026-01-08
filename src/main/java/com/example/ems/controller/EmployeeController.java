package com.example.ems.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.ems.entiry.Employee;
import com.example.ems.repository.EmployeeRepository;

import jakarta.validation.Valid;

@Controller
public class EmployeeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @GetMapping("/employees")
    public String getAllEmployees(Model model){
        List<Employee> employees=employeeRepository.findAll();
        model.addAttribute("employees", employees);
        return "emp-dir-page";
    }

    @GetMapping("/save")  //as we are sending 
    public String saveEmployee(@Valid @ModelAttribute("employee") Employee employee ,BindingResult bindingResult){
        //Annotation that binds a method parameter(employee) to a named model attribute(th:object="{employee}"), exposed to a web view.
        if(bindingResult.hasErrors())
            return "add-update-emp";
        else{
            employeeRepository.save(employee);
            return "redirect:/employees";
        }
    }

    @GetMapping("/showFormForAdd")
    public String showFormForAdd(Model model){
        Employee employee=new Employee();
        model.addAttribute("employee", employee);
        return "add-update-emp";
    }

    @GetMapping("/showFormForUpdate")
    public String showFormForUpdate(@RequestParam("employee.id") int id,Model model){
        Optional<Employee> employee=employeeRepository.findById(id);
        model.addAttribute("employee", employee);
        return "add-update-emp";
    }

    @GetMapping("/delete")
    public String deleteEmployee(@RequestParam("employee.id") int id){
        employeeRepository.deleteById(id);
        return "redirect:/employees";
    }

    
}
