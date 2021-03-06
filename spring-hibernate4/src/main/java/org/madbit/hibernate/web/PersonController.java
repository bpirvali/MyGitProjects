package org.madbit.hibernate.web;

import org.madbit.hibernate.dao.MyDAO;
import org.madbit.hibernate.entity.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PersonController {
	
	@Autowired
	MyDAO myDAO;
	
	@RequestMapping(value="/addPerson", method=RequestMethod.POST) 
	public String addPerson(@RequestParam("firstname") String firstname, @RequestParam("lastname") String lastname, Model model) {
		Person p = new Person();
		p.setFirst_name(firstname);
		p.setLast_name(lastname);
		
		myDAO.addPerson(p);
		
		return "/result.jsp";
	}
}
