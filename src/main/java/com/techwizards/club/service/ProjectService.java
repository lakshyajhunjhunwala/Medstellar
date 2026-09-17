package com.techwizards.club.service;

import com.techwizards.club.model.Project;
import com.techwizards.club.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project createProject(Project project, String username) {
        if (project.getName() == null || project.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Project name cannot be empty");
        }
        
        project.setLeadName(username);
        project.setMemberCount(1);
        project.setStatus("Planning");
        return projectRepository.save(project);
    }

    public Optional<Project> joinProject(Long id, String username) {
        Optional<Project> projectOpt = projectRepository.findById(id);
        if (projectOpt.isEmpty()) {
            return Optional.empty();
        }

        Project project = projectOpt.get();
        // Increment member count
        project.setMemberCount(project.getMemberCount() + 1);
        // Switch status to Active if it was Planning
        if ("Planning".equals(project.getStatus())) {
            project.setStatus("Active");
        }
        return Optional.of(projectRepository.save(project));
    }
}
